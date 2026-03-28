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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Session state (observable) ─────────────────────────

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    val sessionCount: Int get() = _sessions.value.size

    /**
     * Called by the UI to register a screen update callback.
     * The callback MUST call TerminalView.onScreenUpdated().
     * Thread-safe: always dispatched to main thread.
     */
    var onScreenUpdated: (() -> Unit)? = null

    // ── Preferences bridge ──────────────────────────────────

    /** Set by the UI layer so the service can check preferences. */
    @Volatile var bellEnabled: Boolean = true

    // ── Locks ──────────────────────────────────────────────

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    val isWakeLockHeld: Boolean get() = wakeLock?.isHeld == true

    // ── Clipboard ──────────────────────────────────────────

    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // ── Session client callbacks ───────────────────────────

    private val sessionClient = object : TerminalSessionClient {

        override fun onTextChanged(changedSession: TerminalSession) {
            // Must invalidate on main thread (TerminalView.invalidate)
            val callback = onScreenUpdated ?: return
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback()
            } else {
                mainHandler.post(callback)
            }
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            _sessions.update { it.toList() }
            onTextChanged(changedSession) // also refresh screen
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            _sessions.update { current ->
                current.filter { it !== finishedSession }
            }
            updateNotification()
            // Don't stopSelf here — let removeSession handle it
            // to avoid double-stop race condition
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            if (!text.isNullOrEmpty()) {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.app_name), text)
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
            if (!bellEnabled) return
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(50L)
        }

        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
            Log.d(TAG, "Shell PID set: $pid")
        }

        override fun getTerminalCursorStyle(): Int =
            TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR

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
                _sessions.value = emptyList()
                releaseLocks()
                stopSelf()
            }
            ACTION_NEW_SESSION -> {
                createSession()
            }
            ACTION_TOGGLE_WAKELOCK -> {
                toggleWakeLock()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        onScreenUpdated = null // prevent memory leak
        _sessions.value.forEach { it.finishIfRunning() }
        releaseLocks()
        super.onDestroy()
    }

    // ── Public API ─────────────────────────────────────────

    fun createSession(): TerminalSession {
        val shell = findShell()
        val env = buildEnvironment(shell)
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

        // Write welcome banner + short prompt on first session
        if (_sessions.value.size == 1 && isFirstLaunch) {
            isFirstLaunch = false
            mainHandler.postDelayed({
                session.write(buildWelcomeCommands())
            }, 300) // Small delay to let shell initialize
        }

        return session
    }

    private var isFirstLaunch = true

    private fun buildWelcomeCommands(): String {
        // Use printf with actual escape chars (not literal \033)
        // \u001b = ESC character that the shell interprets correctly
        val esc = "\\x1b"
        return buildString {
            append("clear\n")
            append("printf '\\n'\n")
            append("printf '\\n'\n")
            append("printf '\\n'\n")
            append("printf '         ${esc}[38;5;208m╔══════════════════════════╗${esc}[0m\\n'\n")
            append("printf '         ${esc}[38;5;208m║${esc}[0m                          ${esc}[38;5;208m║${esc}[0m\\n'\n")
            append("printf '         ${esc}[38;5;208m║${esc}[0m   ${esc}[1;38;5;208m███╗${esc}[0m ${esc}[1;38;5;214mNovaTerm${esc}[0m      ${esc}[38;5;208m║${esc}[0m\\n'\n")
            append("printf '         ${esc}[38;5;208m║${esc}[0m   ${esc}[1;38;5;208m╚══╝${esc}[0m ${esc}[38;5;246mv0.1.0${esc}[0m        ${esc}[38;5;208m║${esc}[0m\\n'\n")
            append("printf '         ${esc}[38;5;208m║${esc}[0m                          ${esc}[38;5;208m║${esc}[0m\\n'\n")
            append("printf '         ${esc}[38;5;208m║${esc}[0m   ${esc}[38;5;246mby ${esc}[38;5;214mPrometeoDEV${esc}[0m       ${esc}[38;5;208m║${esc}[0m\\n'\n")
            append("printf '         ${esc}[38;5;208m║${esc}[0m                          ${esc}[38;5;208m║${esc}[0m\\n'\n")
            append("printf '         ${esc}[38;5;208m╚══════════════════════════╝${esc}[0m\\n'\n")
            append("printf '\\n'\n")
            append("printf '    ${esc}[38;5;246m• Pinch to zoom text${esc}[0m\\n'\n")
            append("printf '    ${esc}[38;5;246m• Long-press keys for popups${esc}[0m\\n'\n")
            append("printf '    ${esc}[38;5;246m• + button for new sessions${esc}[0m\\n'\n")
            append("printf '\\n'\n")
            // Set short colored prompt using printf-safe method
            append("PS1='\$ '\n")
        }
    }

    fun removeSession(index: Int) {
        _sessions.update { list ->
            if (index !in list.indices) return@update list
            list[index].finishIfRunning()
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
        // 4 hour timeout as safety net (auto-release if app crashes)
        wakeLock?.acquire(4 * 60 * 60 * 1000L)

        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
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
        val firstRun = !home.isDirectory
        if (firstRun) home.mkdirs()
        if (!tmpdir.isDirectory) tmpdir.mkdirs()

        if (firstRun) {
            setupFirstRun(home)
        }

        return home.absolutePath
    }

    private fun setupFirstRun(home: File) {
        // Create a short PS1 prompt (the full path is too long on Android)
        val profile = File(home, ".profile")
        if (!profile.exists()) {
            profile.writeText(buildString {
                appendLine("# NovaTerm - short prompt")
                appendLine("export PS1='\\[\\e[38;5;208m\\]>\\[\\e[0m\\] '")
                appendLine()
            })
        }

        // Create MOTD welcome message
        val motd = File(home, ".motd")
        if (!motd.exists()) {
            motd.writeText(buildString {
                appendLine()
                appendLine("\u001b[38;5;208m  ╭─────────────────────────────╮\u001b[0m")
                appendLine("\u001b[38;5;208m  │\u001b[0m  \u001b[1mNovaTerm\u001b[0m v0.1.0            \u001b[38;5;208m│\u001b[0m")
                appendLine("\u001b[38;5;208m  │\u001b[0m  Next-gen Android Terminal   \u001b[38;5;208m│\u001b[0m")
                appendLine("\u001b[38;5;208m  ╰─────────────────────────────╯\u001b[0m")
                appendLine()
                appendLine("\u001b[38;5;246m  Tips:\u001b[0m")
                appendLine("\u001b[38;5;246m  • Pinch to zoom text\u001b[0m")
                appendLine("\u001b[38;5;246m  • Long-press extra keys for popups\u001b[0m")
                appendLine("\u001b[38;5;246m  • Swipe from left edge for drawer\u001b[0m")
                appendLine("\u001b[38;5;246m  • + button to add sessions\u001b[0m")
                appendLine()
            })
        }

        // Create .shrc that shows MOTD and sources .profile
        val shrc = File(home, ".shrc")
        if (!shrc.exists()) {
            shrc.writeText(buildString {
                appendLine("# NovaTerm shell init")
                appendLine("[ -f \"\$HOME/.profile\" ] && . \"\$HOME/.profile\"")
                appendLine("[ -f \"\$HOME/.motd\" ] && cat \"\$HOME/.motd\"")
                appendLine()
            })
        }
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

    private fun buildEnvironment(shell: String): Array<String> {
        val base = filesDir.parentFile?.absolutePath ?: filesDir.absolutePath
        val prefix = "$base/usr"
        val home = "$base/home"

        return arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=$home",
            "PREFIX=$prefix",
            "SHELL=$shell",
            "LANG=en_US.UTF-8",
            "PATH=$prefix/bin:$prefix/bin/applets:/system/bin",
            "TMPDIR=$prefix/tmp",
            "LD_LIBRARY_PATH=$prefix/lib",
            "ENV=$home/.shrc",  // Loaded by sh on startup
            "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}",
        )
    }

    // ── Notifications ──────────────────────────────────────

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val newSessionIntent = PendingIntent.getService(
            this, REQ_NEW_SESSION,
            Intent(this, TerminalService::class.java).setAction(ACTION_NEW_SESSION),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val wakeLockIntent = PendingIntent.getService(
            this, REQ_TOGGLE_WAKELOCK,
            Intent(this, TerminalService::class.java).setAction(ACTION_TOGGLE_WAKELOCK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val exitIntent = PendingIntent.getService(
            this, REQ_EXIT,
            Intent(this, TerminalService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val count = _sessions.value.size
        val lockLabel = if (isWakeLockHeld) "WL: ON" else "WL: OFF"

        return NotificationCompat.Builder(this, NovaTermApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notification_title_running))
            .setContentText(getString(R.string.notification_sessions, count))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_input_add, getString(R.string.action_new_session), newSessionIntent)
            .addAction(android.R.drawable.ic_lock_lock, lockLabel, wakeLockIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.action_exit), exitIntent)
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
        private const val REQ_NEW_SESSION = 2
        private const val REQ_TOGGLE_WAKELOCK = 3

        const val ACTION_EXIT = "com.novaterm.app.action.EXIT"
        const val ACTION_NEW_SESSION = "com.novaterm.app.action.NEW_SESSION"
        const val ACTION_TOGGLE_WAKELOCK = "com.novaterm.app.action.TOGGLE_WAKELOCK"
    }
}
