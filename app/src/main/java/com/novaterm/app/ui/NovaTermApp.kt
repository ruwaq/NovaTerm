package com.novaterm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novaterm.app.R
import com.novaterm.app.ui.components.CloseSessionDialog
import com.novaterm.app.ui.components.DrawerContent
import com.novaterm.app.ui.components.RenameSessionDialog
import com.novaterm.app.ui.components.SessionCreationFailedDialog
import com.novaterm.app.ui.components.SessionTabBar
import com.novaterm.app.ui.components.StatusLine
import com.novaterm.app.ui.viewmodel.TerminalViewModel
import com.novaterm.feature.settings.ui.ColorSchemePickerScreen
import com.novaterm.core.llm.ModelState
import com.novaterm.feature.settings.ui.SettingsScreen
import com.novaterm.feature.terminal.ui.components.ExtraKeysBar
import com.novaterm.feature.terminal.ui.components.HistoryEntry
import com.novaterm.feature.terminal.ui.components.HistorySearchSheet
import com.novaterm.feature.terminal.ui.components.SuggestionBar
import com.novaterm.feature.terminal.ui.pane.PaneTreeView
import com.novaterm.feature.terminal.ui.pane.SplitDirection
import com.novaterm.feature.terminal.ui.screen.TerminalScreen
import com.novaterm.view.TerminalView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaTermApp(
    viewModel: TerminalViewModel,
    onVoiceInput: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val service by viewModel.service.collectAsState()
    val modelState = service?.modelManager?.state?.collectAsState()?.value ?: ModelState.Idle
    // Unpack frequently-used fields for readability
    val sessions = uiState.sessions
    val selectedTab = uiState.currentSessionIndex
    val ctrlActive = uiState.ctrlActive
    val altActive = uiState.altActive
    val showSettings = uiState.showSettings
    val showOnboarding = uiState.showOnboarding
    val sessionNames = uiState.sessionNames
    val suggestion = uiState.suggestion
    val isInPipMode = uiState.isInPipMode
    val paneLayout = uiState.currentPaneLayout
    val focusedPaneSession = uiState.focusedPaneSession
    val view = LocalView.current

    // Sync preferences to service
    LaunchedEffect(preferences.bellEnabled, preferences.useRustBackend, preferences.scrollbackLines, service) {
        service?.bellEnabled?.set(preferences.bellEnabled)
        service?.useRustBackend?.set(preferences.useRustBackend)
        service?.scrollbackLines?.set(preferences.scrollbackLines)
    }

    // Sync MCP preferences to service and start/stop server
    LaunchedEffect(preferences.mcpEnabled, preferences.mcpPort, service) {
        service?.mcpEnabled?.set(preferences.mcpEnabled)
        service?.mcpPort?.set(preferences.mcpPort)
        service?.startMcpServerIfEnabled()
    }

    // Sync LLM preference — only loads model when user enables it
    LaunchedEffect(preferences.llmEnabled, service) {
        service?.llmEnabled?.set(preferences.llmEnabled)
        service?.updateLlmState()
    }

    // ── Full-screen overlays (onboarding, about, settings) ───
    if (showOnboarding) {
        ColorSchemePickerScreen(onSchemeSelected = viewModel::completeOnboarding)
        return
    }

    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
        return
    }

    if (showSettings) {
        BackHandler { viewModel.hideSettings() }
        SettingsScreen(
            preferences = preferences,
            onPreferencesChanged = viewModel::updatePreferences,
            onBack = viewModel::hideSettings,
            onResetToDefaults = viewModel::resetPreferencesToDefaults,
            modelState = modelState,
            onDownloadModel = { modelId -> service?.modelManager?.startDownload(modelId) },
            onCancelDownload = { service?.modelManager?.cancelDownload() },
            onDeleteModel = { service?.modelManager?.deleteModel() },
            mcpAuthToken = service?.mcpAuthToken,
            onInstallAiTool = { command ->
                // Write the install command to the active terminal session and switch back
                val currentSessions = sessions
                val idx = selectedTab.coerceIn(0, (currentSessions.size - 1).coerceAtLeast(0))
                if (idx in currentSessions.indices) {
                    currentSessions[idx].write(command + "\n")
                }
                viewModel.hideSettings()
            },
        )
        return
    }

    // ── Local state ──────────────────────────────────────────
    var showHistory by remember { mutableStateOf(false) }
    var renamingTabIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }
    // OSC 133 prompt navigation function (set by active TerminalScreen)
    var jumpToPrompt by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    // Reference to the active tab's TerminalView (for output search)
    var activeTerminalView by remember { mutableStateOf<TerminalView?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val safeIndex = selectedTab.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))

    // ── Dialogs ──────────────────────────────────────────────
    uiState.sessionToClose?.let { index ->
        CloseSessionDialog(
            sessionIndex = index,
            onConfirm = viewModel::confirmCloseSession,
            onDismiss = viewModel::cancelCloseSession,
        )
    }

    if (uiState.sessionCreationFailed) {
        SessionCreationFailedDialog(
            onRetry = { viewModel.dismissSessionCreationError(); viewModel.createSession() },
            onDismiss = viewModel::dismissSessionCreationError,
        )
    }

    renamingTabIndex?.let { index ->
        RenameSessionDialog(
            currentName = renameText,
            onRename = { name ->
                if (index in sessions.indices) viewModel.renameSession(index, name)
            },
            onDismiss = { renamingTabIndex = null },
        )
    }

    // ── PiP mode — terminal only, no chrome ────────────────
    if (isInPipMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (sessions.isNotEmpty() && safeIndex in sessions.indices) {
                val pipSession = sessions[safeIndex]
                TerminalScreen(
                    session = pipSession,
                    fontSize = preferences.fontSize,
                    fontFamily = preferences.fontFamily,
                    colorScheme = preferences.colorScheme,
                    keepScreenOn = preferences.keepScreenOn,
                    ctrlActive = false,
                    altActive = false,
                    backIsEscape = false,
                    onModifiersConsumed = {},
                    onTabAcceptSuggestion = { null },
                    onBlockComplete = { command, exitCode ->
                        val sessionId = "session_$safeIndex"
                        val cwd = pipSession.cwd
                        service?.blockStore?.insertBlock(
                            sessionId = sessionId,
                            command = command,
                            exitCode = exitCode,
                            cwd = cwd,
                        )
                        service?.predictionEngine?.onCommandExecuted(
                            sessionId = sessionId,
                            command = command,
                            cwd = cwd,
                        )
                        viewModel.refreshSuggestion()
                    },
                    onPromptNavigatorReady = {},
                    onViewReady = { terminalView ->
                        registerRustScreenCallback(service, pipSession.mHandle, terminalView)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                DisposableEffect(pipSession.mHandle) {
                    onDispose {
                        service?.unregisterScreenCallback(pipSession.mHandle)
                    }
                }
            }
        }
        return
    }

    // ── Main layout ──────────────────────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                sessionCount = sessions.size,
                selectedIndex = safeIndex,
                onSelectSession = { viewModel.selectSession(it); scope.launch { drawerState.close() } },
                onCloseSession = viewModel::requestCloseSession,
                onNewSession = { viewModel.createSession(); scope.launch { drawerState.close() } },
                onSettings = { viewModel.showSettings(); scope.launch { drawerState.close() } },
                onAbout = { showAbout = true; scope.launch { drawerState.close() } },
            )
        },
    ) {
        Scaffold(
            topBar = {},
            bottomBar = {
                Column(modifier = Modifier.imePadding()) {
                    if (sessions.isNotEmpty()) {
                        StatusLine(
                            cwd = sessions.getOrNull(safeIndex)?.cwd ?: "",
                            onPromptUp = jumpToPrompt?.let { jump -> { jump(-1) } },
                            onPromptDown = jumpToPrompt?.let { jump -> { jump(1) } },
                        )

                        SessionTabBar(
                            sessions = sessions,
                            selectedPage = safeIndex,
                            sessionNames = sessionNames,
                            onSelectTab = { viewModel.selectSession(it) },
                            onLongClickTab = { index ->
                                renameText = sessions.getOrNull(index)?.title?.takeIf { it.isNotBlank() } ?: ""
                                renamingTabIndex = index
                            },
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = { showHistory = true },
                            onSettingsClick = viewModel::showSettings,
                            onNewSession = viewModel::createSession,
                        )
                    }

                    // Command suggestion — subtle bar, only visible when there's a prediction
                    SuggestionBar(
                        suggestion = suggestion,
                        onAccept = { cmd ->
                            val accepted = viewModel.acceptSuggestion()
                            if (accepted != null) {
                                sessions.getOrNull(safeIndex)?.write(accepted)
                            }
                        },
                        onDismiss = { viewModel.dismissSuggestion() },
                    )

                    AnimatedVisibility(
                        visible = preferences.showExtraKeys,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        ExtraKeysBar(
                            onKey = { code ->
                                if (safeIndex in sessions.indices) {
                                    // Tab or Right arrow accepts suggestion if one is active
                                    if ((code == "\t" || code == "\u001b[C") && suggestion != null) {
                                        val accepted = viewModel.acceptSuggestion()
                                        if (accepted != null) {
                                            sessions.getOrNull(safeIndex)?.write(accepted)
                                            return@ExtraKeysBar
                                        }
                                    }
                                    // In split mode, write to the focused pane session
                                    val targetIndex = if (paneLayout != null && focusedPaneSession in sessions.indices) {
                                        focusedPaneSession
                                    } else {
                                        safeIndex
                                    }
                                    sessions.getOrNull(targetIndex)?.write(resolveKeyInput(code, ctrlActive, altActive))
                                    viewModel.resetModifiers()
                                }
                            },
                            onCtrlToggle = viewModel::toggleCtrl,
                            onAltToggle = viewModel::toggleAlt,
                            onKeyboardToggle = {
                                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                    as? InputMethodManager
                                @Suppress("DEPRECATION")
                                imm?.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
                            },
                            onVoiceInput = onVoiceInput,
                            onSplitHorizontal = { viewModel.splitPane(SplitDirection.HORIZONTAL) },
                            onSplitVertical = { viewModel.splitPane(SplitDirection.VERTICAL) },
                            onCloseSplitPane = { viewModel.closeSplitPane() },
                            hasSplitPanes = paneLayout != null,
                            ctrlActive = ctrlActive,
                            altActive = altActive,
                            hapticEnabled = preferences.hapticFeedback,
                            extraKeysStyle = preferences.extraKeysStyle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                // Note: do NOT use imePadding() here — Scaffold already positions
                // bottomBar above the keyboard. Adding imePadding would double the
                // space, pushing terminal content to the middle of the screen.
            ) {
                if (sessions.isNotEmpty() && safeIndex in sessions.indices) {
                    val currentSession = sessions[safeIndex]

                    if (paneLayout != null) {
                        // Split pane mode — render pane tree
                        PaneTreeView(
                            node = paneLayout!!,
                            sessions = sessions,
                            focusedSessionIndex = focusedPaneSession,
                            fontSize = preferences.fontSize,
                            fontFamily = preferences.fontFamily,
                            colorScheme = preferences.colorScheme,
                            keepScreenOn = preferences.keepScreenOn,
                            ctrlActive = ctrlActive,
                            altActive = altActive,
                            backIsEscape = preferences.backIsEscape,
                            onModifiersConsumed = viewModel::resetModifiers,
                            onFocusPane = viewModel::focusPane,
                            onBlockComplete = { sessionIndex, command, exitCode ->
                                val sessionId = "session_$sessionIndex"
                                val cwd = sessions.getOrNull(sessionIndex)?.cwd
                                service?.blockStore?.insertBlock(
                                    sessionId = sessionId,
                                    command = command,
                                    exitCode = exitCode,
                                    cwd = cwd,
                                )
                                service?.predictionEngine?.onCommandExecuted(
                                    sessionId = sessionId,
                                    command = command,
                                    cwd = cwd,
                                )
                                viewModel.refreshSuggestion()
                            },
                            onTabAcceptSuggestion = { viewModel.acceptSuggestion() },
                            onViewReady = { sessionIndex, terminalView ->
                                if (sessionIndex == focusedPaneSession) {
                                    activeTerminalView = terminalView
                                }
                                val s = sessions.getOrNull(sessionIndex) ?: return@PaneTreeView
                                registerRustScreenCallback(service, s.mHandle, terminalView)
                            },
                            onPromptNavigatorReady = { nav -> jumpToPrompt = nav },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Single TerminalScreen — one View, one InputConnection, swap sessions via attachSession()
                        TerminalScreen(
                            session = currentSession,
                            fontSize = preferences.fontSize,
                            fontFamily = preferences.fontFamily,
                            colorScheme = preferences.colorScheme,
                            keepScreenOn = preferences.keepScreenOn,
                            ctrlActive = ctrlActive,
                            altActive = altActive,
                            backIsEscape = preferences.backIsEscape,
                            onModifiersConsumed = viewModel::resetModifiers,
                            onTabAcceptSuggestion = { viewModel.acceptSuggestion() },
                            onBlockComplete = { command, exitCode ->
                                val sessionId = "session_$safeIndex"
                                val cwd = currentSession.cwd
                                service?.blockStore?.insertBlock(
                                    sessionId = sessionId,
                                    command = command,
                                    exitCode = exitCode,
                                    cwd = cwd,
                                )
                                service?.predictionEngine?.onCommandExecuted(
                                    sessionId = sessionId,
                                    command = command,
                                    cwd = cwd,
                                )
                                viewModel.refreshSuggestion()
                            },
                            onPromptNavigatorReady = { nav -> jumpToPrompt = nav },
                            onViewReady = { terminalView ->
                                activeTerminalView = terminalView
                                registerRustScreenCallback(service, currentSession.mHandle, terminalView)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Cleanup old callbacks when session changes
                    DisposableEffect(currentSession.mHandle) {
                        onDispose {
                            service?.unregisterScreenCallback(currentSession.mHandle)
                        }
                    }
                } else if (sessions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(stringResource(R.string.status_starting), color = MaterialTheme.colorScheme.onBackground)
                            Button(onClick = viewModel::createSession) {
                                Text(stringResource(R.string.action_new_session))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── History search ───────────────────────────────────────
    if (showHistory) {
        val sessionId = remember(safeIndex) { "session_$safeIndex" }
        val blocks = remember(service, sessionId) {
            service?.blockStore?.getRecentBlocks(sessionId)
                ?.map { block ->
                    HistoryEntry(
                        command = block.command, cwd = block.cwd,
                        exitCode = block.exitCode, timestamp = block.timestamp,
                        isAiGenerated = block.isAiGenerated,
                    )
                } ?: emptyList()
        }
        val currentSession = sessions.getOrNull(safeIndex)
        HistorySearchSheet(
            entries = blocks,
            onSelect = { currentSession?.write(it) },
            onDismiss = { showHistory = false },
            terminalBuffer = currentSession?.emulator?.screen,
            onScrollToRow = { row -> activeTerminalView?.scrollToRow(row) },
        )
    }
}

/** Resolve key input applying modifier keys (Ctrl/Alt). */
private fun resolveKeyInput(code: String, ctrlActive: Boolean, altActive: Boolean): String {
    if (code.length != 1) return code
    return when {
        ctrlActive -> {
            val c = code[0]
            if (c in 'a'..'z' || c in 'A'..'Z') (c.uppercaseChar() - 'A' + 1).toChar().toString()
            else code
        }
        altActive -> "\u001b$code"
        else -> code
    }
}

/**
 * Register the Rust engine screen callback on a [TerminalView].
 * Extracted to avoid repeating the same 15-line block in PiP, split-pane, and single-screen modes.
 */
internal fun registerRustScreenCallback(
    service: com.novaterm.app.service.TerminalService?,
    handle: String,
    terminalView: TerminalView,
) {
    service?.registerScreenCallback(handle) {
        if (service.useRustBackend.get()) {
            val engine = service.getRustEngine(handle)
            if (engine != null) {
                val grid = engine.getGrid()
                val cursor = engine.getCursor()
                if (grid != null) {
                    val dims = engine.getDimensions()
                    terminalView.setRustGrid(
                        grid, dims.rows, dims.columns,
                        cursor.row, cursor.column,
                        2, true,
                    )
                }
            }
        } else {
            terminalView.clearRustGrid()
        }
        terminalView.onScreenUpdated()
    }
}
