package com.novaterm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.novaterm.feature.settings.ui.SettingsScreen
import com.novaterm.feature.terminal.ui.components.ExtraKeysBar
import com.novaterm.feature.terminal.ui.screen.TerminalScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    val novaColors = LocalNovaTermColors.current

    val service by viewModel.service.collectAsState()

    // Sync preferences to service only when they change
    LaunchedEffect(preferences.bellEnabled, service) {
        service?.bellEnabled = preferences.bellEnabled
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
                onCloseSession = viewModel::removeSession,
                onNewSession = {
                    viewModel.createSession()
                    scope.launch { drawerState.close() }
                },
                onSettings = {
                    viewModel.showSettings()
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
                                    modifier = Modifier.height(40.dp),
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
                                            text = session.title?.takeIf { it.isNotBlank() }
                                                ?: stringResource(R.string.tab_session, index + 1),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
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
