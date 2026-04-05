package com.novaterm.app.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novaterm.app.service.TerminalService
import com.novaterm.feature.settings.data.PreferencesRepository
import com.novaterm.feature.settings.data.TerminalPreferences
import com.novaterm.feature.terminal.ui.pane.PaneManager
import com.novaterm.feature.terminal.ui.pane.PaneNode
import com.novaterm.feature.terminal.ui.pane.SplitDirection
import com.novaterm.feature.terminal.ui.pane.leafCount
import com.novaterm.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel that owns the service binding and all terminal UI state.
 *
 * Survives configuration changes and process death (for the binding -
 * the service itself is a foreground service that persists independently).
 * Exposes sessions as [StateFlow] so Compose observes changes reactively.
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val preferences: StateFlow<TerminalPreferences> = prefsRepo.preferences

    // ── Service binding ────────────────────────────────────

    private val _service = MutableStateFlow<TerminalService?>(null)
    val service: StateFlow<TerminalService?> = _service.asStateFlow()

    /**
     * Sessions derived from the bound service's StateFlow.
     * When service is null, emits empty list.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<TerminalSession>> = _service
        .flatMapLatest { svc -> svc?.sessions ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as? TerminalService.LocalBinder)?.service ?: return
            _service.value = svc
            if (svc.sessionCount == 0) {
                val saved = svc.getSavedSessions()
                if (saved.isNotEmpty()) {
                    // Restore sessions in their original directories
                    saved.forEach { meta ->
                        svc.createSessionInDir(meta.cwd)
                    }
                    svc.clearSavedSessions()
                } else {
                    if (svc.createSession() == null) {
                        _sessionCreationFailed.value = true
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            _service.value = null
        }
    }

    private var bound = false

    fun bindService() {
        if (bound) return
        val app = getApplication<Application>()
        app.bindService(
            Intent(app, TerminalService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE or Context.BIND_ABOVE_CLIENT,
        )
        bound = true
    }

    fun unbindService() {
        if (!bound) return
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
            // Already unbound
        }
        bound = false
    }

    override fun onCleared() {
        unbindService()
        super.onCleared()
    }

    // ── Session UI state ───────────────────────────────────

    private val _currentSessionIndex = MutableStateFlow(0)
    val currentSessionIndex: StateFlow<Int> = _currentSessionIndex.asStateFlow()

    /** Custom tab names set by the user (index → name). Overrides terminal title. */
    private val _sessionNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val sessionNames: StateFlow<Map<Int, String>> = _sessionNames.asStateFlow()

    private val _ctrlActive = MutableStateFlow(false)
    val ctrlActive: StateFlow<Boolean> = _ctrlActive.asStateFlow()

    private val _altActive = MutableStateFlow(false)
    val altActive: StateFlow<Boolean> = _altActive.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _showOnboarding = MutableStateFlow(!prefsRepo.isOnboardingCompleted)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    /** Current command suggestion from the prediction engine. Null = no suggestion shown. */
    private val _suggestion = MutableStateFlow<String?>(null)
    val suggestion: StateFlow<String?> = _suggestion.asStateFlow()

    private val _sessionCreationFailed = MutableStateFlow(false)
    val sessionCreationFailed: StateFlow<Boolean> = _sessionCreationFailed.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    // ── Pane management (built-in multiplexer) ────────────

    /** Per-tab pane managers. Key = tab index. Only populated for tabs with splits. */
    private val _paneManagers = java.util.concurrent.ConcurrentHashMap<Int, PaneManager>()

    /** The pane layout for the current tab. Null = single pane (default). */
    private val _currentPaneLayout = MutableStateFlow<PaneNode?>(null)
    val currentPaneLayout: StateFlow<PaneNode?> = _currentPaneLayout.asStateFlow()

    /** Which session index is focused within a split pane. */
    private val _focusedPaneSession = MutableStateFlow(-1)
    val focusedPaneSession: StateFlow<Int> = _focusedPaneSession.asStateFlow()

    /**
     * Split the currently focused pane (or the current tab if no split exists yet).
     * Creates a new session for the new pane.
     */
    fun splitPane(direction: SplitDirection) {
        val svc = _service.value ?: return
        val currentSessions = sessions.value
        val tabIndex = _currentSessionIndex.value
        if (tabIndex !in currentSessions.indices) return

        // Get or create PaneManager for this tab
        val manager = _paneManagers.getOrPut(tabIndex) {
            PaneManager().also { it.init(tabIndex) }
        }
        if (!manager.hasSplit()) {
            manager.init(tabIndex)
        }

        // Check max panes
        val layout = manager.getCurrentLayout() ?: return
        if (layout.leafCount() >= PaneManager.MAX_PANES) return

        // Create a new session for the split
        val newSession = svc.createSession() ?: return
        val newSessionIndex = svc.sessionCount - 1

        if (!manager.splitFocusedPane(direction, newSessionIndex)) {
            // Split failed — remove the session we just created
            svc.removeSession(newSessionIndex)
            return
        }

        _currentPaneLayout.value = manager.getCurrentLayout()
        _focusedPaneSession.value = manager.getFocusedPane()
    }

    /** Close the currently focused pane within a split. */
    fun closeSplitPane() {
        val tabIndex = _currentSessionIndex.value
        val manager = _paneManagers[tabIndex] ?: return
        val focusedSession = manager.getFocusedPane()

        val remaining = manager.closePane(focusedSession)
        if (remaining == null || remaining.size <= 1) {
            // No more splits — revert to single-pane mode
            _paneManagers.remove(tabIndex)
            _currentPaneLayout.value = null
            _focusedPaneSession.value = -1
        } else {
            _currentPaneLayout.value = manager.getCurrentLayout()
            _focusedPaneSession.value = manager.getFocusedPane()
        }

        // Remove the actual session
        val svc = _service.value ?: return
        svc.removeSession(focusedSession)

        // Adjust indices in all PaneManagers
        for ((_, mgr) in _paneManagers) {
            mgr.adjustIndicesAfterRemoval(focusedSession)
        }
    }

    /** Set focus to a specific pane within the split layout. */
    fun focusPane(sessionIndex: Int) {
        val tabIndex = _currentSessionIndex.value
        val manager = _paneManagers[tabIndex] ?: return
        manager.setFocusedPane(sessionIndex)
        _focusedPaneSession.value = sessionIndex
    }

    /** Refresh the pane layout state when switching tabs. */
    private fun refreshPaneLayout() {
        val tabIndex = _currentSessionIndex.value
        val manager = _paneManagers[tabIndex]
        if (manager != null && manager.hasSplit()) {
            _currentPaneLayout.value = manager.getCurrentLayout()
            _focusedPaneSession.value = manager.getFocusedPane()
        } else {
            _currentPaneLayout.value = null
            _focusedPaneSession.value = -1
        }
    }

    // ── Actions ────────────────────────────────────────────

    fun createSession() {
        val svc = _service.value ?: return
        val session = svc.createSession()
        if (session != null) {
            _currentSessionIndex.value = svc.sessionCount - 1
            _sessionCreationFailed.value = false
        } else {
            _sessionCreationFailed.value = true
        }
    }

    fun dismissSessionCreationError() {
        _sessionCreationFailed.value = false
    }

    // ── Session close confirmation ─────────────────────────

    private val _sessionToClose = MutableStateFlow<Int?>(null)
    val sessionToClose: StateFlow<Int?> = _sessionToClose.asStateFlow()

    fun requestCloseSession(index: Int) {
        val svc = _service.value ?: return
        val sessions = svc.sessions.value
        // If session is still running, ask confirmation
        if (index in sessions.indices && sessions[index].isRunning) {
            _sessionToClose.value = index
        } else {
            removeSession(index)
        }
    }

    fun confirmCloseSession() {
        _sessionToClose.value?.let { removeSession(it) }
        _sessionToClose.value = null
    }

    fun cancelCloseSession() {
        _sessionToClose.value = null
    }

    private fun removeSession(index: Int) {
        val svc = _service.value ?: return
        svc.removeSession(index)
        // Rebuild session names map: remove the deleted index and shift higher indices down
        _sessionNames.update { names ->
            buildMap {
                for ((i, name) in names) {
                    when {
                        i < index -> put(i, name)
                        i > index -> put(i - 1, name)
                        // i == index: dropped (removed session)
                    }
                }
            }
        }
        val newCount = svc.sessionCount
        if (_currentSessionIndex.value >= newCount) {
            _currentSessionIndex.value = (newCount - 1).coerceAtLeast(0)
        }
    }

    fun selectSession(index: Int) {
        _currentSessionIndex.value = index
        refreshPaneLayout()
    }

    fun renameSession(index: Int, name: String) {
        _sessionNames.update { it + (index to name) }
    }

    fun getSessionName(index: Int): String? = _sessionNames.value[index]

    fun toggleCtrl() {
        _ctrlActive.value = !_ctrlActive.value
    }

    fun toggleAlt() {
        _altActive.value = !_altActive.value
    }

    fun resetModifiers() {
        _ctrlActive.value = false
        _altActive.value = false
    }

    fun updatePreferences(newPrefs: TerminalPreferences) {
        prefsRepo.update(newPrefs)
    }

    fun resetPreferencesToDefaults() {
        prefsRepo.resetToDefaults()
    }

    fun completeOnboarding(colorScheme: String) {
        prefsRepo.update(prefsRepo.preferences.value.copy(colorScheme = colorScheme))
        prefsRepo.completeOnboarding()
        _showOnboarding.value = false
    }

    fun showSettings() {
        _showSettings.value = true
    }

    fun hideSettings() {
        _showSettings.value = false
    }

    fun toggleWakeLock() {
        _service.value?.toggleWakeLock()
    }

    // ── Command prediction ────────────────────────────────

    /** Update the suggestion based on what the user just executed. */
    fun refreshSuggestion() {
        val svc = _service.value ?: return
        if (!svc.predictionEngineReady) {
            _suggestion.value = null
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val sessionIndex = _currentSessionIndex.value
                val sessionId = "session_$sessionIndex"
                val cwd = sessions.value.getOrNull(sessionIndex)?.cwd

                // Re-check service and engine readiness inside coroutine
                val currentSvc = _service.value ?: return@launch
                if (!currentSvc.predictionEngineReady) {
                    _suggestion.value = null
                    return@launch
                }

                // Try N-gram prediction first (fast, no model needed)
                val predictions = currentSvc.predictionEngine.predict(
                    sessionId = sessionId,
                    cwd = cwd,
                    maxResults = 1,
                )
                val ngramResult = predictions.firstOrNull()?.command

                if (ngramResult != null) {
                    _suggestion.value = ngramResult
                    return@launch
                }

                // Fallback to LLM if enabled and ready (slower but smarter)
                val llm = currentSvc.llmEngine
                if (llm != null && llm.state.value == com.novaterm.core.llm.LlmState.READY) {
                    val recentCommands = currentSvc.predictionEngine.recentCommands(sessionId, limit = 5)
                    val context = com.novaterm.core.llm.TerminalContext(
                        recentCommands = recentCommands.map { cmd ->
                            com.novaterm.core.llm.CommandEntry(command = cmd)
                        },
                        cwd = cwd,
                    )
                    val llmSuggestion = llm.suggest(context)
                    _suggestion.value = llmSuggestion?.command
                } else {
                    _suggestion.value = null
                }
            } catch (e: Exception) {
                // Service destroyed or engine uninitialized during execution
                _suggestion.value = null
            }
        }
    }

    /** Accept the current suggestion — write it to the terminal. */
    fun acceptSuggestion(): String? {
        val cmd = _suggestion.value
        _suggestion.value = null
        return cmd
    }

    /** Dismiss suggestion without accepting. */
    fun dismissSuggestion() {
        _suggestion.value = null
    }
}
