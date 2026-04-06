package com.novaterm.app.service

import android.os.Handler
import android.util.Log
import com.novaterm.app.BuildConfig
import com.novaterm.core.session.persistence.SessionMetadata
import com.novaterm.core.session.persistence.SessionStore
import com.novaterm.core.mcp.prediction.PredictionEngine
import com.novaterm.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages periodic and on-demand persistence of terminal session metadata.
 *
 * Extracted from TerminalService to separate persistence concerns from
 * service lifecycle management.
 */
class SessionPersistenceManager(
    private val sessionStore: SessionStore,
    private val sessionsSnapshot: () -> List<TerminalSession>,
    private val shellFinder: () -> String,
    private val defaultCwd: () -> String,
    private val predictionEngine: () -> PredictionEngine?,
    private val mainHandler: Handler,
    private val serviceScope: CoroutineScope,
    private val isServiceDestroyed: () -> Boolean,
) {
    private val saveRunnable = object : Runnable {
        override fun run() {
            if (isServiceDestroyed()) return
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val sessions = sessionsSnapshot()
                    if (sessions.isNotEmpty()) {
                        saveSessionMetadata()
                    }
                    predictionEngine()?.save()
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic save error", e)
                }
            }
            if (!isServiceDestroyed()) {
                mainHandler.postDelayed(this, SAVE_INTERVAL_MS)
            }
        }
    }

    fun startPeriodicSave() {
        mainHandler.postDelayed(saveRunnable, SAVE_INTERVAL_MS)
    }

    fun stopPeriodicSave() {
        mainHandler.removeCallbacks(saveRunnable)
    }

    @Synchronized
    fun saveSessionMetadata() {
        val snapshot = sessionsSnapshot()
        val metadata = snapshot.mapIndexed { index, session ->
            SessionMetadata(
                id = index,
                shell = shellFinder(),
                cwd = session.cwd ?: defaultCwd(),
                title = session.title?.takeIf { it.isNotBlank() } ?: "shell",
            )
        }
        sessionStore.save(metadata)
        if (BuildConfig.DEBUG) Log.d(TAG, "Saved ${metadata.size} session(s) metadata")
    }

    fun getSavedSessions(): List<SessionMetadata> = sessionStore.load()

    fun clearSavedSessions() = sessionStore.clear()

    companion object {
        private const val TAG = "NovaTerm"
        private const val SAVE_INTERVAL_MS = 30_000L
    }
}
