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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novaterm.app.R
import com.novaterm.app.service.TerminalService
import com.novaterm.feature.settings.ui.SettingsScreen
import com.novaterm.feature.terminal.ui.components.ExtraKeysBar
import com.novaterm.feature.terminal.ui.screen.TerminalScreen
import com.novaterm.app.ui.viewmodel.TerminalViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaTermApp(
    service: TerminalService?,
    onNewSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    viewModel: TerminalViewModel = viewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val selectedTab by viewModel.currentSessionIndex.collectAsState()
    val ctrlActive by viewModel.ctrlActive.collectAsState()
    val altActive by viewModel.altActive.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()

    val sessions = service?.sessions ?: emptyList()

    if (showSettings) {
        SettingsScreen(
            preferences = preferences,
            onPreferencesChanged = { viewModel.updatePreferences(it) },
            onBack = { viewModel.hideSettings() }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "NovaTerm",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineSmall
                )

                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                sessions.forEachIndexed { index, _ ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.tab_session, index + 1)) },
                        selected = index == selectedTab,
                        onClick = {
                            viewModel.selectSession(index)
                            scope.launch { drawerState.close() }
                        },
                        badge = {
                            IconButton(onClick = { onCloseSession(index) }) {
                                Icon(Icons.Default.Close, "Close session", tint = Color(0xFFFB4934))
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text(stringResource(R.string.action_new_session)) },
                    selected = false,
                    onClick = {
                        onNewSession()
                        viewModel.selectSession(sessions.size)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.action_settings)) },
                    selected = false,
                    onClick = {
                        viewModel.showSettings()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (sessions.size > 1) {
                    Column {
                        TopAppBar(
                            title = { Text("NovaTerm") },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.showSettings() }) {
                                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                                }
                                IconButton(onClick = onNewSession) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_session))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF1D2021),
                                titleContentColor = Color(0xFFEBDBB2),
                                navigationIconContentColor = Color(0xFFEBDBB2),
                                actionIconContentColor = Color(0xFFEBDBB2),
                            )
                        )
                        ScrollableTabRow(
                            selectedTabIndex = selectedTab.coerceIn(0, (sessions.size - 1).coerceAtLeast(0)),
                            containerColor = Color(0xFF282828),
                            contentColor = Color(0xFFEBDBB2),
                            edgePadding = 0.dp
                        ) {
                            sessions.forEachIndexed { index, _ ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { viewModel.selectSession(index) },
                                    text = { Text(stringResource(R.string.tab_session, index + 1)) }
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
                            val currentIndex = selectedTab.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))
                            if (currentIndex in sessions.indices) {
                                val session = sessions[currentIndex]
                                val bytes = if (ctrlActive && code.length == 1) {
                                    val c = code[0]
                                    if (c in 'a'..'z' || c in 'A'..'Z') {
                                        val ctrl = (c.uppercaseChar() - 'A' + 1).toChar()
                                        ctrl.toString()
                                    } else {
                                        code
                                    }
                                } else if (altActive && code.length == 1) {
                                    "\u001b$code"
                                } else {
                                    code
                                }
                                session.write(bytes)
                                viewModel.resetModifiers()
                            }
                        },
                        onCtrlToggle = { viewModel.toggleCtrl() },
                        onAltToggle = { viewModel.toggleAlt() },
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = Color(0xFF1D2021)
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (sessions.isNotEmpty()) {
                    val currentIndex = selectedTab.coerceIn(0, sessions.size - 1)
                    TerminalScreen(
                        session = sessions[currentIndex],
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Starting terminal...",
                            color = Color(0xFFEBDBB2)
                        )
                    }
                }
            }
        }
    }
}
