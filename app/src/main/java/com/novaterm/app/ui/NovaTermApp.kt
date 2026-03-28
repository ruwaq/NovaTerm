package com.novaterm.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

    // Sync preferences to service
    val service = viewModel.service.collectAsState().value
    service?.bellEnabled = preferences.bellEnabled

    if (showSettings) {
        androidx.activity.compose.BackHandler { viewModel.hideSettings() }
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
            topBar = {
                Column {
                    if (sessions.isNotEmpty()) {
                        TopAppBar(
                            title = {
                                Text(stringResource(R.string.app_name))
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Default.Menu,
                                        contentDescription = stringResource(R.string.cd_open_drawer),
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = viewModel::showSettings) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.action_settings),
                                    )
                                }
                                IconButton(onClick = viewModel::createSession) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.action_new_session),
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                        ScrollableTabRow(
                            selectedTabIndex = safeIndex,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            edgePadding = 0.dp,
                        ) {
                            sessions.forEachIndexed { index, _ ->
                                Tab(
                                    selected = safeIndex == index,
                                    onClick = { viewModel.selectSession(index) },
                                    text = {
                                        Text(stringResource(R.string.tab_session, index + 1))
                                    },
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
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
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (sessions.isNotEmpty() && safeIndex in sessions.indices) {
                    TerminalScreen(
                        session = sessions[safeIndex],
                        fontSize = preferences.fontSize,
                        keepScreenOn = preferences.keepScreenOn,
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        backIsEscape = preferences.backIsEscape,
                        onModifiersConsumed = viewModel::resetModifiers,
                        onViewReady = { terminalView ->
                            // Connect the TerminalView's screen update to the service callback.
                            // Re-connects on every view creation (handles rotation + tab switch).
                            // Thread-safe: service dispatches to main thread via Handler.
                            service?.onScreenUpdated = { terminalView.onScreenUpdated() }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.status_starting),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            androidx.compose.material3.Button(
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
                    IconButton(onClick = { onCloseSession(index) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close_session),
                            tint = novaColors.destructive,
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text(stringResource(R.string.action_new_session)) },
            selected = false,
            onClick = onNewSession,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
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
