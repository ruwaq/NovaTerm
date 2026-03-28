package com.novaterm.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.novaterm.app.NovaTermApp
import com.novaterm.app.R
import com.novaterm.app.ui.MainActivity
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Foreground service managing terminal sessions.
 *
 * Exposes session list as [StateFlow] so the UI layer can observe
 * changes reactively. Handles wake/wifi locks for long-running tasks
 * and implements all [TerminalSessionClient] callbacks.
 */
class TerminalService : Service() {

    // ── Binder ─────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        val service: TerminalService get() = this@TerminalService
    }

    private val binder = LocalBinder()

    // ── Session state (observable) ─────────────────────────

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    val sessionCount: Int get() = _sessions.value.size

    /** Called by the UI to register a screen update callback (TerminalView.onScreenUpdated). */
    var onScreenUpdated: (() -> Unit)? = null

    // ── Locks ──────────────────────────────────────────────

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    val isWakeLockHeld: Boolean get() = wakeLock?.isHeld == true

    // ── Clipboard bridge (set by Activity) ─────────────────

    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // ── Session client callbacks ───────────────────────────

    private val sessionClient = object : TerminalSessionClient {

        override fun onTextChanged(changedSession: TerminalSession) {
            // Invalidate the TerminalView canvas so it redraws with new content
            onScreenUpdated?.invoke()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            _sessions.update { it.toList() }
            onScreenUpdated?.invoke()
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            _sessions.update { current ->
                current.filter { it !== finishedSession }
            }
            updateNotification()
            if (_sessions.value.isEmpty()) stopSelf()
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            if (!text.isNullOrEmpty()) {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("NovaTerm", text)
                )
            }
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this@TerminalService)
                session?.write(text.toString())
            }
        }

        override fun onBell(session: TerminalSession) {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(50L)
        }

        override fun onColorsChanged(session: TerminalSession) {
            // Reserved for future theme-sync support
        }

        override fun onTerminalCursorStateChange(state: Boolean) {
            // Reserved for cursor blink toggling
        }

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
            Log.d(TAG, "Shell PID set: $pid")
        }

        override fun getTerminalCursorStyle(): Int =
            TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK

        override fun logError(tag: String?, message: String?) {
            Log.e(tag ?: TAG, message ?: "")
        }

        override fun logWarn(tag: String?, message: String?) {
            Log.w(tag ?: TAG, message ?: "")
        }

        override fun logInfo(tag: String?, message: String?) {
            Log.i(tag ?: TAG, message ?: "")
        }

        override fun logDebug(tag: String?, message: String?) {
            Log.d(tag ?: TAG, message ?: "")
        }

        override fun logVerbose(tag: String?, message: String?) {
            Log.v(tag ?: TAG, message ?: "")
        }

        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Log.e(tag ?: TAG, message, e)
        }

        override fun logStackTrace(tag: String?, e: Exception?) {
            Log.e(tag ?: TAG, "Error", e)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXIT -> {
                _sessions.value.forEach { it.finishIfRunning() }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        _sessions.value.forEach { it.finishIfRunning() }
        releaseLocks()
        super.onDestroy()
    }

    // ── Public API ─────────────────────────────────────────

    fun createSession(): TerminalSession {
        val shell = findShell()
        val env = buildEnvironment()
        val cwd = resolveHomeDir()

        Log.i(TAG, "Creating session: shell=$shell cwd=$cwd")

        val session = TerminalSession(
            shell,
            cwd,
            arrayOf(shell),
            env,
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            sessionClient,
        )

        Log.i(TAG, "Session created: pid=${session.pid} running=${session.isRunning}")

        _sessions.update { it + session }
        updateNotification()
        return session
    }

    fun removeSession(index: Int) {
        val current = _sessions.value
        if (index !in current.indices) return

        current[index].finishIfRunning()
        _sessions.update { list ->
            list.filterIndexed { i, _ -> i != index }
        }
        updateNotification()
        if (_sessions.value.isEmpty()) stopSelf()
    }

    fun toggleWakeLock() {
        if (wakeLock?.isHeld == true) {
            releaseLocks()
        } else {
            acquireLocks()
        }
        updateNotification()
    }

    // ── Lock management ────────────────────────────────────

    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "novaterm:service-wakelock",
            )
        }
        @Suppress("WakelockTimeout")
        wakeLock?.acquire()

        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "novaterm:service-wifilock",
            )
        }
        wifiLock?.acquire()
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        if (wifiLock?.isHeld == true) wifiLock?.release()
        wifiLock = null
    }

    // ── Shell environment ──────────────────────────────────

    private fun resolveHomeDir(): String {
        val base = filesDir.parentFile?.absolutePath ?: filesDir.absolutePath
        val home = File("$base/home")
        val tmpdir = File("$base/usr/tmp")
        // Ensure HOME and TMPDIR exist
        if (!home.isDirectory) home.mkdirs()
        if (!tmpdir.isDirectory) tmpdir.mkdirs()
        return home.absolutePath
    }

    private fun findShell(): String {
        val base = filesDir.parentFile?.absolutePath ?: return "/system/bin/sh"
        val candidates = listOf(
            "$base/usr/bin/bash",
            "$base/usr/bin/sh",
            "/system/bin/sh",
        )
        return candidates.firstOrNull { File(it).canExecute() }
            ?: "/system/bin/sh"
    }

    private fun buildEnvironment(): Array<String> {
        val base = filesDir.parentFile?.absolutePath ?: filesDir.absolutePath
        val prefix = "$base/usr"
        val home = "$base/home"

        return arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=$home",
            "PREFIX=$prefix",
            "LANG=en_US.UTF-8",
            "PATH=$prefix/bin:$prefix/bin/applets:/system/bin",
            "TMPDIR=$prefix/tmp",
            "LD_LIBRARY_PATH=$prefix/lib",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}",
        )
    }

    // ── Notifications ──────────────────────────────────────

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val exitIntent = PendingIntent.getService(
            this,
            REQ_EXIT,
            Intent(this, TerminalService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val count = _sessions.value.size
        val lockIcon = if (wakeLock?.isHeld == true) " \uD83D\uDD12" else ""

        return NotificationCompat.Builder(this, NovaTermApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notification_title_running))
            .setContentText(getString(R.string.notification_sessions, count) + lockIcon)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.action_exit),
                exitIntent,
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val TAG = "NovaTerm"
        private const val NOTIFICATION_ID = 1337
        private const val REQ_EXIT = 1

        const val ACTION_EXIT = "com.novaterm.app.action.EXIT"
    }
}
