package com.novaterm.app.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.novaterm.app.service.TerminalService
import com.novaterm.core.mcp.security.ApprovalRequest
import com.novaterm.core.session.manager.AgentStatus
import com.novaterm.core.session.manager.AgentWorkspace
import com.novaterm.core.session.manager.SessionGroupManager
import com.novaterm.core.session.persistence.SessionGroup
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel that owns the service binding and all terminal UI state.
 *
 * Survives configuration changes and process death (for the binding -
 * the service itself is a foreground service that persists independently).
 * Exposes sessions as [StateFlow] so Compose observes changes reactively.
 */
class TerminalViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    private val _savedStateHandle = savedStateHandle

    /** Session group manager — auto-groups sessions by project/agent. */
    val groupManager = SessionGroupManager(
        sessionStore = com.novaterm.core.session.persistence.SessionStore(application),
        homeDir = application.filesDir.absolutePath,
    )

    /** Home directory for group detection fallback. */
    private val homeDir = application.filesDir.absolutePath

    companion object {
        private const val KEY_CURRENT_SESSION_INDEX = "current_session_index"
        private const val KEY_SESSION_NAMES = "session_names"
        private const val KEY_FOCUSED_PANE_SESSION = "focused_pane_session"
    }

    // ── State persistence ───────────────────────────────────
    private fun restoreStateFromSavedStateHandle() {
        // Restore current session index with validation
        val savedCurrentIndex = _savedStateHandle.get<Int>(KEY_CURRENT_SESSION_INDEX)
        if (savedCurrentIndex != null && savedCurrentIndex >= 0) {
            _currentSessionIndex.value = savedCurrentIndex // Validated >= 0 above
        }

        // Restore session names with validation
        val savedSessionNames = _savedStateHandle.get<Map<Int, String>>(KEY_SESSION_NAMES)
        if (savedSessionNames != null && savedSessionNames.isNotEmpty()) {
            _sessionNames.value = savedSessionNames
        }

        // Restore focused pane session with validation
        val savedFocusedPaneSession = _savedStateHandle.get<Int>(KEY_FOCUSED_PANE_SESSION)
        if (savedFocusedPaneSession != null && savedFocusedPaneSession >= -1) {
            _focusedPaneSession.value = savedFocusedPaneSession
        }
    }

    override fun onCleared() {
        // Save UI state before ViewModel is cleared
        saveStateToSavedStateHandle()
        // Persist session groups
        groupManager.saveGroups()
        super.onCleared()
    }

    private fun saveStateToSavedStateHandle() {
        // Save current session index with validation
        _savedStateHandle.set(KEY_CURRENT_SESSION_INDEX, _currentSessionIndex.value.coerceAtLeast(0))

        // Save session names
        _savedStateHandle.set(KEY_SESSION_NAMES, _sessionNames.value)

        // Save focused pane session
        _savedStateHandle.set(KEY_FOCUSED_PANE_SESSION, _focusedPaneSession.value)

        // Note: _currentPaneLayout is not saved as it's derived from the current session state
        // and will be restored when the service is connected and the session is loaded
    }

    // ── Service binding ────────────────────────────────────

    private val _service = MutableStateFlow<TerminalService?>(null)

    init {
        // Restore UI state from saved instance
        restoreStateFromSavedStateHandle()
        // Load session groups from persistent storage
        groupManager.initialize()
        // Sync grouping toggle from preferences
        viewModelScope.launch {
            prefsRepo.preferences.collect { prefs ->
                groupManager.setGroupingEnabled(prefs.sessionGroupingEnabled)
            }
        }
    }
    val service: StateFlow<TerminalService?> = _service.asStateFlow()

    /** Terminal preferences (font size, color scheme, etc.) exposed for UI. */
    val preferences: StateFlow<TerminalPreferences> = prefsRepo.preferences

    /**
     * Sessions derived from the bound service's StateFlow.
     * When service is null, emits empty list.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<TerminalSession>> = _service
        .flatMapLatest { svc -> svc?.sessions ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pending MCP approval request for DANGEROUS tool calls (e.g., run_command). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val mcpApprovalRequest: StateFlow<ApprovalRequest?> = _service
        .flatMapLatest { svc -> svc?.mcpApprovalRequest ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Agent workspaces derived from the service's AgentOrchestrator.
     * Reactive — updates whenever workspace status changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val agentWorkspaces: StateFlow<List<AgentWorkspace>> = _service
        .flatMapLatest { svc ->
            svc?.getAgentOrchestrator()?.workspacesState ?: flowOf(emptyList())
        }
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
            // Save initial state
            saveStateToSavedStateHandle()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            _service.value = null
            bound = false
            // Auto-rebind after system kills service with immediate attempt
            // Using BIND_AUTO_CREATE flag which automatically creates the service if it doesn't exist
            viewModelScope.launch {
                bindService()
            }
        }

        override fun onBindingDied(name: ComponentName) {
            _service.value = null
            bound = false
            viewModelScope.launch {
                bindService()
            }
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
        saveStateToSavedStateHandle()
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

    // Declared here (before uiState) so the combined flow can reference it.
    private val _sessionToClose = MutableStateFlow<Int?>(null)
    val sessionToClose: StateFlow<Int?> = _sessionToClose.asStateFlow()


    /**
     * Combined UI state consumed by [NovaTermApp].
     * Replaces 14 individual [collectAsState] calls with a single subscription.
     */
    val uiState: StateFlow<TerminalUiState> = combine(
        // Group 1: session navigation state
        combine(sessions, _currentSessionIndex, _sessionNames, _currentPaneLayout, _focusedPaneSession) {
                s, idx, names, layout, focusedPane ->
            arrayOf<Any?>(s, idx, names, layout, focusedPane)
        },
        // Group 2: input / suggestion state
        combine(_ctrlActive, _altActive, _suggestion) { ctrl, alt, sug ->
            arrayOf<Any?>(ctrl, alt, sug)
        },
        // Group 3: overlay / dialog state
        combine(_showSettings, _showOnboarding, _isInPipMode, _sessionToClose, _sessionCreationFailed) {
                settings, onboarding, pip, toClose, failed ->
            arrayOf<Any?>(settings, onboarding, pip, toClose, failed)
        },
        // Group 4: session grouping state
        combine(groupManager.groups, groupManager.sessionGroupMap, groupManager.groupingEnabled) {
                groups, sessionGroupMap, groupingEnabled ->
            Triple(groups, sessionGroupMap, groupingEnabled)
        },
    ) { nav, input, overlay, grouping ->
        @Suppress("UNCHECKED_CAST")
        val groups = grouping.first as List<com.novaterm.core.session.persistence.SessionGroup>
        val sessionGroupMap = grouping.second as Map<Int, String>
        val groupingEnabled = grouping.third as Boolean
        TerminalUiState(
            sessions             = nav[0] as List<TerminalSession>,
            currentSessionIndex  = nav[1] as Int,
            sessionNames         = nav[2] as Map<Int, String>,
            currentPaneLayout    = nav[3] as PaneNode?,
            focusedPaneSession   = nav[4] as Int,
            ctrlActive           = input[0] as Boolean,
            altActive            = input[1] as Boolean,
            suggestion           = input[2] as String?,
            showSettings         = overlay[0] as Boolean,
            showOnboarding       = overlay[1] as Boolean,
            isInPipMode          = overlay[2] as Boolean,
            sessionToClose       = overlay[3] as Int?,
            sessionCreationFailed = overlay[4] as Boolean,
            groups               = groupManager.getGroupsWithSessionCounts(),
            sessionGroupMap      = sessionGroupMap,
            groupingEnabled      = groupingEnabled,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TerminalUiState(showOnboarding = !prefsRepo.isOnboardingCompleted),
    )

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
        saveStateToSavedStateHandle()
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
        // Save state to handle configuration changes
        saveStateToSavedStateHandle()

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
        // Save state to handle configuration changes
        saveStateToSavedStateHandle()
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
        // Save state to handle configuration changes
        saveStateToSavedStateHandle()
    }

    // ── Actions ────────────────────────────────────────────

    fun createSession() {
        val svc = _service.value ?: return
        val session = svc.createSession()
        if (session != null) {
            val newIndex = svc.sessionCount - 1
            _currentSessionIndex.value = newIndex
            _sessionCreationFailed.value = false
            // Auto-assign to group based on CWD
            val cwd = session.cwd.ifBlank { homeDir }
            val groupId = groupManager.detectGroupForCwd(cwd)
            groupManager.assignSessionToGroup(newIndex, groupId)
            saveStateToSavedStateHandle()
        } else {
            _sessionCreationFailed.value = true
        }
    }

    fun dismissSessionCreationError() {
        _sessionCreationFailed.value = false
    }

    // ── Session close confirmation ─────────────────────────

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
        // Unassign from group and adjust all session indices
        groupManager.unassignSession(index)
        groupManager.adjustSessionIndicesAfterRemoval(index)
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
        // Use observed sessions flow size — svc.sessionCount may lag the flow
        val newCount = sessions.value.size
        if (_currentSessionIndex.value >= newCount) {
            _currentSessionIndex.value = (newCount - 1).coerceAtLeast(0)
        }
        // Clean up orphaned PaneManagers for removed indices
        _paneManagers.keys.removeAll { it >= newCount }
        // Save state to handle configuration changes
        saveStateToSavedStateHandle()
    }

    fun selectSession(index: Int) {
        _currentSessionIndex.value = index
        // Clean up PaneManagers for indices that no longer exist
        _paneManagers.keys.removeAll { it >= sessions.value.size }
        refreshPaneLayout()
        saveStateToSavedStateHandle()
    }

    fun renameSession(index: Int, name: String) {
        _sessionNames.update { it + (index to name) }
        saveStateToSavedStateHandle()
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

    fun showSettings() {
        _showSettings.value = true
    }

    fun hideSettings() {
        _showSettings.value = false
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

                // Re-check service readiness inside coroutine
                val currentSvc = _service.value ?: return@launch
                if (!currentSvc.predictionEngineReady) {
                    _suggestion.value = null
                    return@launch
                }

                // N-gram prediction (fast, no model needed)
                val predictions = currentSvc.predictionEngine.predict(
                    sessionId = sessionId,
                    cwd = cwd,
                    maxResults = 1,
                )
                _suggestion.value = predictions.firstOrNull()?.command
            } catch (e: Exception) {
                if (com.novaterm.app.BuildConfig.DEBUG) Log.d("NovaTerm", "Suggestion failed: ${e.message}")
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

    // ── Session group actions ──────────────────────────────────

    /** Toggle a group's expanded/collapsed state in the tab bar. */
    fun toggleGroup(groupId: String) {
        groupManager.toggleGroupExpanded(groupId)
    }

    /** Create a new custom group with the given name and optional color. */
    fun createGroup(name: String, color: String = SessionGroup.DEFAULT_COLOR): SessionGroup {
        return groupManager.createGroup(name, color)
    }

    /** Move a session to a different group. No-op if index is out of bounds. */
    fun moveSessionToGroup(sessionIndex: Int, targetGroupId: String) {
        if (sessionIndex < 0 || sessionIndex >= sessions.value.size) return
        groupManager.moveSessionToGroup(sessionIndex, targetGroupId)
    }

    /** Toggle session grouping on/off. */
    fun setGroupingEnabled(enabled: Boolean) {
        groupManager.setGroupingEnabled(enabled)
    }

    // ── Agent workspace actions ────────────────────────────

    /** Switch to the session tab for an agent workspace. */
    fun openAgentSession(workspace: AgentWorkspace) {
        val sessionId = workspace.sessionId
        if (sessionId >= 0) {
            selectSession(sessionId)
        }
    }

    /** Pause or resume an agent workspace (SIGSTOP/SIGCONT). */
    fun pauseAgent(workspaceId: String) {
        val svc = _service.value ?: return
        val workspace = svc.getAgentOrchestrator().findById(workspaceId) ?: return
        if (workspace.status == AgentStatus.RUNNING) {
            svc.pauseWorkspace(workspaceId)
        } else if (workspace.status == AgentStatus.PAUSED) {
            svc.resumeWorkspace(workspaceId)
        }
    }

    /** Kill an agent workspace — terminates process and cleans up. */
    fun killAgent(workspaceId: String) {
        val svc = _service.value ?: return
        svc.killWorkspace(workspaceId)
    }

    /**
     * Run `git diff` in a workspace's directory.
     * Returns raw unified diff output, or empty string on failure.
     */
    fun runAgentDiff(workspaceId: String): String {
        val svc = _service.value ?: return ""
        return svc.getAgentOrchestrator().runDiff(workspaceId)
    }

    /**
     * Approve (commit) all changes in a workspace.
     * @return true if commit succeeded
     */
    fun approveAgentChanges(workspaceId: String): Boolean {
        val svc = _service.value ?: return false
        return svc.getAgentOrchestrator().approveChanges(workspaceId)
    }

    /**
     * Reject (discard) all changes in a workspace.
     * @return true if checkout succeeded
     */
    fun rejectAgentChanges(workspaceId: String): Boolean {
        val svc = _service.value ?: return false
        return svc.getAgentOrchestrator().rejectChanges(workspaceId)
    }
}
