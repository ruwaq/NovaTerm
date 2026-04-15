package com.novaterm.app.ui.viewmodel

import com.novaterm.core.session.manager.GroupWithCount
import com.novaterm.feature.terminal.ui.pane.PaneNode
import com.novaterm.terminal.TerminalSession

/**
 * Single snapshot of all terminal UI state consumed by [NovaTermApp].
 *
 * Combining all flows into one [StateFlow] reduces the number of active
 * coroutine subscriptions from 13+ down to 1, cutting Compose recomposition
 * overhead on every state change.
 */
data class TerminalUiState(
    val sessions: List<TerminalSession> = emptyList(),
    val currentSessionIndex: Int = 0,
    val sessionNames: Map<Int, String> = emptyMap(),
    val ctrlActive: Boolean = false,
    val altActive: Boolean = false,
    val showSettings: Boolean = false,
    val showOnboarding: Boolean = false,
    val suggestion: String? = null,
    val sessionCreationFailed: Boolean = false,
    val isInPipMode: Boolean = false,
    val currentPaneLayout: PaneNode? = null,
    val focusedPaneSession: Int = -1,
    val sessionToClose: Int? = null,
    /** Session groups with counts, for grouped tab bar. */
    val groups: List<GroupWithCount> = emptyList(),
    /** Maps session index → group ID. */
    val sessionGroupMap: Map<Int, String> = emptyMap(),
    /** Whether session grouping is enabled in settings. */
    val groupingEnabled: Boolean = true,
)
