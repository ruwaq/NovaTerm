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
 * Data class to hold UI state that needs persistence across sessions
 */
data class TerminalViewModelUIState(
    val currentSessionIndex: Int = 0,
    val sessionNames: Map<Int, String> = emptyMap(),
    val focusedPaneSession: Int = -1
)

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
    private val uiStateProvider: () -> TerminalViewModelUIState? = { null },
    private val uiStateConsumer: (TerminalViewModelUIState) -> Unit = {},
    private val blockStore: com.novaterm.core.session.persistence.db.BlockStore? = null,
) {
    private var pruneCounter = 0

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
                    // Prune BlockStore every 10th save (~5 minutes) to avoid unbounded growth
                    pruneCounter++
                    if (pruneCounter >= 10) {
                        blockStore?.pruneOrphanedData()
                        pruneCounter = 0
                    }
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
        val uiState = uiStateProvider()
        SessionMetadata(
            id = index,
            shell = shellFinder(),
            cwd = session.cwd ?: defaultCwd(),
            title = session.title?.takeIf { it.isNotBlank() } ?: "shell",
            activeTabIndex = uiState?.currentSessionIndex ?: index,
            tabNames = uiState?.sessionNames ?: emptyMap(),
            focusedPaneSession = uiState?.focusedPaneSession ?: -1
        )
    }
    sessionStore.save(metadata)
    if (BuildConfig.DEBUG) Log.d(TAG, "Saved ${metadata.size} session(s) metadata")
}

    fun getSavedSessions(): List<SessionMetadata> = sessionStore.load()

fun restoreUIState(uiStateConsumer: (TerminalViewModelUIState) -> Unit) {
    val savedSessions = getSavedSessions()
    if (savedSessions.isNotEmpty()) {
        val firstSession = savedSessions.firstOrNull()
        if (firstSession != null) {
            uiStateConsumer.invoke(TerminalViewModelUIState(
                currentSessionIndex = firstSession.activeTabIndex,
                sessionNames = firstSession.tabNames,
                focusedPaneSession = firstSession.focusedPaneSession
            ))
        }
    }
}

    fun clearSavedSessions() = sessionStore.clear()

    companion object {
        private const val TAG = "NovaTerm"
        private const val SAVE_INTERVAL_MS = 30_000L
    }
}
