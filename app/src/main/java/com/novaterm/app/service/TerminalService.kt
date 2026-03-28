package com.novaterm.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.novaterm.app.NovaTermApp
import com.novaterm.app.R
import com.novaterm.app.ui.MainActivity
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class TerminalService : Service() {

    inner class LocalBinder : Binder() {
        val service: TerminalService get() = this@TerminalService
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    private val _sessions = java.util.concurrent.CopyOnWriteArrayList<TerminalSession>()
    val sessions: List<TerminalSession> get() = _sessions.toList()

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {}
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) {
            val index = _sessions.indexOf(finishedSession)
            if (index >= 0) {
                _sessions.removeAt(index)
                updateNotification()
                if (_sessions.isEmpty()) stopSelf()
            }
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
        override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
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

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        _sessions.forEach { it.finishIfRunning() }
        releaseWakeLock()
        super.onDestroy()
    }

    fun createSession(): TerminalSession {
        val shell = findShell()
        val env = buildEnvironment()
        val cwd = filesDir.parentFile?.absolutePath ?: filesDir.absolutePath

        val session = TerminalSession(
            shell,
            cwd,
            arrayOf(shell),
            env,
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            sessionClient
        )
        _sessions.add(session)
        updateNotification()
        return session
    }

    fun removeSession(index: Int) {
        if (index in _sessions.indices) {
            _sessions[index].finishIfRunning()
            _sessions.removeAt(index)
            updateNotification()
            if (_sessions.isEmpty()) {
                stopSelf()
            }
        }
    }

    fun toggleWakeLock() {
        if (wakeLock?.isHeld == true) {
            releaseWakeLock()
        } else {
            acquireWakeLock()
        }
        updateNotification()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "novaterm:service-wakelock"
            )
        }
        @Suppress("WakelockTimeout")
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun findShell(): String {
        val candidates = listOf(
            "${filesDir.parentFile?.absolutePath}/usr/bin/bash",
            "${filesDir.parentFile?.absolutePath}/usr/bin/sh",
            "/system/bin/sh"
        )
        return candidates.firstOrNull { java.io.File(it).canExecute() }
            ?: "/system/bin/sh"
    }

    private fun buildEnvironment(): Array<String> {
        val prefix = "${filesDir.parentFile?.absolutePath}/usr"
        val home = "${filesDir.parentFile?.absolutePath}/home"

        return arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=$home",
            "PREFIX=$prefix",
            "LANG=en_US.UTF-8",
            "PATH=$prefix/bin:/system/bin",
            "TMPDIR=$prefix/tmp",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}"
        )
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val wakeLockStatus = if (wakeLock?.isHeld == true) " | Wake Lock" else ""

        return NotificationCompat.Builder(this, NovaTermApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notification_title_running))
            .setContentText(getString(R.string.notification_sessions, _sessions.size) + wakeLockStatus)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val TAG = "NovaTerm"
        private const val NOTIFICATION_ID = 1337
    }
}
