package com.novaterm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaterm.app.R
import com.novaterm.app.ui.theme.LocalNovaTermColors
import com.novaterm.app.ui.viewmodel.TerminalViewModel
import com.novaterm.feature.settings.ui.ColorSchemePickerScreen
import com.novaterm.feature.settings.ui.SettingsScreen
import com.novaterm.feature.terminal.ui.components.HistoryEntry
import com.novaterm.feature.terminal.ui.components.HistorySearchSheet
import com.novaterm.feature.terminal.ui.components.ExtraKeysBar
import com.novaterm.feature.terminal.ui.screen.TerminalScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NovaTermApp(
    viewModel: TerminalViewModel,
) {
    val sessions by viewModel.sessions.collectAsState()
    val selectedTab by viewModel.currentSessionIndex.collectAsState()
    val ctrlActive by viewModel.ctrlActive.collectAsState()
    val altActive by viewModel.altActive.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    val sessionNames by viewModel.sessionNames.collectAsState()

    val novaColors = LocalNovaTermColors.current

    val service by viewModel.service.collectAsState()

    // Sync preferences to service only when they change
    LaunchedEffect(preferences.bellEnabled, service) {
        service?.bellEnabled = preferences.bellEnabled
    }

    // ── First launch: color scheme picker ──────────────────
    if (showOnboarding) {
        ColorSchemePickerScreen(
            onSchemeSelected = { schemeId ->
                viewModel.completeOnboarding(schemeId)
            },
        )
        return
    }

    // About screen
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

    // History search state
    var showHistory by remember { mutableStateOf(false) }

    // Tab rename state
    var renamingTabIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Clamp index to valid range
    val safeIndex = remember(selectedTab, sessions.size) {
        selectedTab.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))
    }

    // Pager state for swipe between sessions
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { sessions.size.coerceAtLeast(1) },
    )

    // Sync: pager swipe → ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != selectedTab && page in sessions.indices) {
                viewModel.selectSession(page)
            }
        }
    }

    // Sync: ViewModel (tab click, new session) → pager
    LaunchedEffect(safeIndex) {
        if (pagerState.currentPage != safeIndex && sessions.isNotEmpty()) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    // Close confirmation dialog
    val sessionToClose by viewModel.sessionToClose.collectAsState()
    sessionToClose?.let { index ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCloseSession,
            title = { Text(stringResource(R.string.dialog_close_session_title)) },
            text = { Text(stringResource(R.string.dialog_close_session_message, index + 1)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCloseSession) {
                    Text(stringResource(R.string.dialog_close), color = novaColors.destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelCloseSession) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    // Tab rename dialog
    renamingTabIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { renamingTabIndex = null },
            title = { Text(stringResource(R.string.dialog_rename_session_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.dialog_rename_placeholder)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (index in sessions.indices && renameText.isNotBlank()) {
                        viewModel.renameSession(index, renameText.trim())
                    }
                    renamingTabIndex = null
                }) {
                    Text(stringResource(R.string.dialog_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTabIndex = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                sessionCount = sessions.size,
                selectedIndex = safeIndex,
                onSelectSession = { index ->
                    viewModel.selectSession(index)
                    scope.launch { drawerState.close() }
                },
                onCloseSession = viewModel::requestCloseSession,
                onNewSession = {
                    viewModel.createSession()
                    scope.launch { drawerState.close() }
                },
                onSettings = {
                    viewModel.showSettings()
                    scope.launch { drawerState.close() }
                },
                onAbout = {
                    showAbout = true
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {},
            bottomBar = {
                Column(modifier = Modifier.navigationBarsPadding()) {
                // Compact tab bar at bottom, above extra keys
                // ── Status line: directory + git branch ──────
                if (sessions.isNotEmpty()) {
                    val currentSession = sessions.getOrNull(pagerState.currentPage)
                    val cwd = currentSession?.cwd ?: ""
                    val shortPath = remember(cwd) { shortenPath(cwd) }
                    val gitBranch = remember(cwd) { readGitBranch(cwd) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = shortPath,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (gitBranch != null) {
                            Text(
                                text = " $gitBranch",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // ── Tab bar ──────────────────────────────────
                if (sessions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Menu button (compact)
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Scrollable tabs synced with pager
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, (sessions.size - 1).coerceAtLeast(0)),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            edgePadding = 0.dp,
                            modifier = Modifier.weight(1f),
                            divider = {},
                        ) {
                            sessions.forEachIndexed { index, session ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    modifier = Modifier
                                        .height(40.dp)
                                        .combinedClickable(
                                            onClick = {
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            },
                                            onLongClick = {
                                                renameText = session.title?.takeIf { it.isNotBlank() } ?: ""
                                                renamingTabIndex = index
                                            },
                                        ),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        // Status dot: green=running, red=finished
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    color = if (session.isRunning)
                                                        novaColors.accent
                                                    else
                                                        novaColors.destructive,
                                                    shape = androidx.compose.foundation.shape.CircleShape,
                                                )
                                        )
                                        Text(
                                            text = sessionNames[index]
                                                ?: session.title?.takeIf { it.isNotBlank() }
                                                ?: stringResource(R.string.tab_session, index + 1),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        // History search
                        IconButton(
                            onClick = { showHistory = true },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search_history),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Settings (compact)
                        IconButton(
                            onClick = viewModel::showSettings,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // New session (compact)
                        IconButton(
                            onClick = viewModel::createSession,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.action_new_session),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Extra keys bar
                AnimatedVisibility(
                    visible = preferences.showExtraKeys,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    ExtraKeysBar(
                        onKey = { code ->
                            if (safeIndex in sessions.indices) {
                                val bytes = resolveKeyInput(code, ctrlActive, altActive)
                                sessions[safeIndex].write(bytes)
                                viewModel.resetModifiers()
                            }
                        },
                        onCtrlToggle = viewModel::toggleCtrl,
                        onAltToggle = viewModel::toggleAlt,
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        hapticEnabled = preferences.hapticFeedback,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                } // end Column
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(), // Animate content with keyboard (adjustNothing in manifest)
            ) {
                if (sessions.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = drawerState.isClosed && sessions.size > 1,
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
                                onBlockComplete = { command, exitCode ->
                                    service?.blockStore?.insertBlock(
                                        sessionId = "session_$page",
                                        command = command,
                                        exitCode = exitCode,
                                        cwd = sessions.getOrNull(page)?.cwd,
                                    )
                                },
                                onViewReady = { terminalView ->
                                    if (page == pagerState.settledPage) {
                                        service?.onScreenUpdated = { terminalView.onScreenUpdated() }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.status_starting),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Button(
                                onClick = { viewModel.createSession() },
                            ) {
                                Text(stringResource(R.string.action_new_session))
                            }
                        }
                    }
                }
            }
        }
    }

    // History search bottom sheet
    if (showHistory) {
        val blocks = remember(service) {
            service?.blockStore?.getRecentBlocks("session_${pagerState.currentPage}")
                ?.map { block ->
                    HistoryEntry(
                        command = block.command,
                        cwd = block.cwd,
                        exitCode = block.exitCode,
                        timestamp = block.timestamp,
                        isAiGenerated = block.isAiGenerated,
                    )
                } ?: emptyList()
        }

        HistorySearchSheet(
            entries = blocks,
            onSelect = { command ->
                val idx = pagerState.currentPage
                if (idx in sessions.indices) {
                    sessions[idx].write(command)
                }
            },
            onDismiss = { showHistory = false },
        )
    }
}

// ── Extracted composables ──────────────────────────────────

@Composable
private fun DrawerContent(
    sessionCount: Int,
    selectedIndex: Int,
    onSelectSession: (Int) -> Unit,
    onCloseSession: (Int) -> Unit,
    onNewSession: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val novaColors = LocalNovaTermColors.current

    ModalDrawerSheet {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.headlineSmall,
        )

        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        repeat(sessionCount) { index ->
            NavigationDrawerItem(
                label = {
                    Text(stringResource(R.string.tab_session, index + 1))
                },
                selected = index == selectedIndex,
                onClick = { onSelectSession(index) },
                badge = {
                    IconButton(
                        onClick = { onCloseSession(index) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close_session),
                            tint = novaColors.destructive,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_session)) },
            label = { Text(stringResource(R.string.action_new_session)) },
            selected = false,
            onClick = onNewSession,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings)) },
            label = { Text(stringResource(R.string.action_settings)) },
            selected = false,
            onClick = onSettings,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Info, contentDescription = "About") },
            label = { Text("About") },
            selected = false,
            onClick = onAbout,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/**
 * Resolves key input applying modifier keys (Ctrl/Alt).
 */
private fun resolveKeyInput(code: String, ctrlActive: Boolean, altActive: Boolean): String {
    if (code.length != 1) return code

    return when {
        ctrlActive -> {
            val c = code[0]
            if (c in 'a'..'z' || c in 'A'..'Z') {
                (c.uppercaseChar() - 'A' + 1).toChar().toString()
            } else {
                code
            }
        }
        altActive -> "\u001b$code"
        else -> code
    }
}

/**
 * Shorten a path for the status line. Replaces home with ~,
 * truncates intermediate segments to 2 chars (Powerlevel10k style).
 * Example: /data/data/com.novaterm.app/home/projects/novaterm → ~/pr/novaterm
 */
private fun shortenPath(path: String): String {
    if (path.isBlank()) return "~"
    // Replace common home prefixes
    var short = path
        .replace(Regex("/data/data/[^/]+/home"), "~")
        .replace(Regex("/data/data/[^/]+/files/home"), "~")
        .replace("/storage/emulated/0", "/sdcard")

    val parts = short.split("/").filter { it.isNotEmpty() }
    if (parts.size <= 2) return short

    // Keep first (~ or root) and last segment full, shorten middle
    return buildString {
        append(parts.first())
        for (i in 1 until parts.size - 1) {
            append("/")
            append(parts[i].take(2))
        }
        append("/")
        append(parts.last())
    }
}

/**
 * Read the current git branch from .git/HEAD.
 * Returns null if not in a git repo.
 */
private fun readGitBranch(cwd: String): String? {
    if (cwd.isBlank()) return null
    try {
        var dir = java.io.File(cwd)
        // Walk up to find .git directory
        repeat(10) {
            val gitDir = java.io.File(dir, ".git")
            if (gitDir.isDirectory) {
                val head = java.io.File(gitDir, "HEAD")
                if (head.exists()) {
                    val content = head.readText().trim()
                    return if (content.startsWith("ref: refs/heads/")) {
                        content.removePrefix("ref: refs/heads/")
                    } else {
                        content.take(7) // detached HEAD — show short hash
                    }
                }
            }
            dir = dir.parentFile ?: return null
        }
    } catch (_: Exception) { }
    return null
}
