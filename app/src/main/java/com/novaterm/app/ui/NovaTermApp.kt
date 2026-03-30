package com.novaterm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.novaterm.feature.settings.ui.SettingsScreen
import com.novaterm.feature.terminal.ui.components.ExtraKeysBar
import com.novaterm.feature.terminal.ui.components.HistoryEntry
import com.novaterm.feature.terminal.ui.components.HistorySearchSheet
import com.novaterm.feature.terminal.ui.components.SuggestionBar
import com.novaterm.feature.terminal.ui.screen.TerminalScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NovaTermApp(viewModel: TerminalViewModel) {
    val sessions by viewModel.sessions.collectAsState()
    val selectedTab by viewModel.currentSessionIndex.collectAsState()
    val ctrlActive by viewModel.ctrlActive.collectAsState()
    val altActive by viewModel.altActive.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    val sessionNames by viewModel.sessionNames.collectAsState()
    val service by viewModel.service.collectAsState()
    val suggestion by viewModel.suggestion.collectAsState()
    val view = LocalView.current

    // Sync preferences to service
    LaunchedEffect(preferences.bellEnabled, preferences.useRustBackend, service) {
        service?.bellEnabled = preferences.bellEnabled
        service?.useRustBackend = preferences.useRustBackend
    }

    // Sync MCP preferences to service and start/stop server
    LaunchedEffect(preferences.mcpEnabled, preferences.mcpPort, service) {
        service?.mcpEnabled = preferences.mcpEnabled
        service?.mcpPort = preferences.mcpPort
        service?.startMcpServerIfEnabled()
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
        )
        return
    }

    // ── Local state ──────────────────────────────────────────
    var showHistory by remember { mutableStateOf(false) }
    var renamingTabIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }
    // OSC 133 prompt navigation function (set by active TerminalScreen)
    var jumpToPrompt by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val safeIndex = selectedTab.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))

    // ── Pager ────────────────────────────────────────────────
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { sessions.size.coerceAtLeast(1) },
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != selectedTab && page in sessions.indices) viewModel.selectSession(page)
        }
    }

    LaunchedEffect(safeIndex) {
        if (pagerState.currentPage != safeIndex && sessions.isNotEmpty()) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    // ── Dialogs ──────────────────────────────────────────────
    val sessionToClose by viewModel.sessionToClose.collectAsState()
    sessionToClose?.let { index ->
        CloseSessionDialog(
            sessionIndex = index,
            onConfirm = viewModel::confirmCloseSession,
            onDismiss = viewModel::cancelCloseSession,
        )
    }

    val sessionFailed by viewModel.sessionCreationFailed.collectAsState()
    if (sessionFailed) {
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
                Column(modifier = Modifier.navigationBarsPadding()) {
                    if (sessions.isNotEmpty()) {
                        StatusLine(
                            cwd = sessions.getOrNull(pagerState.currentPage)?.cwd ?: "",
                            onPromptUp = jumpToPrompt?.let { jump -> { jump(-1) } },
                            onPromptDown = jumpToPrompt?.let { jump -> { jump(1) } },
                        )

                        SessionTabBar(
                            sessions = sessions,
                            selectedPage = pagerState.currentPage,
                            sessionNames = sessionNames,
                            onSelectTab = { scope.launch { pagerState.animateScrollToPage(it) } },
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
                            if (accepted != null && safeIndex in sessions.indices) {
                                sessions[safeIndex].write(accepted)
                            }
                        },
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
                                            sessions[safeIndex].write(accepted)
                                            return@ExtraKeysBar
                                        }
                                    }
                                    sessions[safeIndex].write(resolveKeyInput(code, ctrlActive, altActive))
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
                            ctrlActive = ctrlActive,
                            altActive = altActive,
                            hapticEnabled = preferences.hapticFeedback,
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
                    .padding(padding)
                    .imePadding(),
            ) {
                if (sessions.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        if (page in sessions.indices) {
                            TerminalScreen(
                                session = sessions[page],
                                fontSize = preferences.fontSize,
                                colorScheme = preferences.colorScheme,
                                keepScreenOn = preferences.keepScreenOn && page == pagerState.settledPage,
                                ctrlActive = ctrlActive,
                                altActive = altActive,
                                backIsEscape = preferences.backIsEscape,
                                onModifiersConsumed = viewModel::resetModifiers,
                                onTabAcceptSuggestion = { viewModel.acceptSuggestion() },
                                onBlockComplete = { command, exitCode ->
                                    val sessionId = "session_$page"
                                    val cwd = sessions.getOrNull(page)?.cwd
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
                                onPromptNavigatorReady = { nav ->
                                    if (page == pagerState.settledPage) jumpToPrompt = nav
                                },
                                onViewReady = { terminalView ->
                                    if (page == pagerState.settledPage) {
                                        service?.onScreenUpdated = {
                                            val session = sessions.getOrNull(page)
                                            if (session != null && service?.useRustBackend == true) {
                                                val engine = service?.getRustEngine(session.mHandle)
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
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
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
        val blocks = remember(service, pagerState.currentPage) {
            service?.blockStore?.getRecentBlocks("session_${pagerState.currentPage}")
                ?.map { block ->
                    HistoryEntry(
                        command = block.command, cwd = block.cwd,
                        exitCode = block.exitCode, timestamp = block.timestamp,
                        isAiGenerated = block.isAiGenerated,
                    )
                } ?: emptyList()
        }
        HistorySearchSheet(
            entries = blocks,
            onSelect = { sessions.getOrNull(pagerState.currentPage)?.write(it) },
            onDismiss = { showHistory = false },
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
