package com.novaterm.feature.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novaterm.core.session.manager.AgentStatus
import com.novaterm.core.session.manager.AgentWorkspace

/**
 * Full-screen dashboard for monitoring AI agent workspaces.
 *
 * Shows a list of [AgentCard]s with real-time status updates,
 * runtime counters, and action buttons (open, pause/resume, kill).
 * Displays an empty state when no agents are running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDashboardScreen(
    workspaces: List<AgentWorkspace>,
    onOpenSession: (AgentWorkspace) -> Unit,
    onPauseResume: (AgentWorkspace) -> Unit,
    onKill: (AgentWorkspace) -> Unit,
    onLaunchAgent: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var workspaceToKill by remember { mutableStateOf<AgentWorkspace?>(null) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Agent Dashboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        val activeCount = workspaces.count { it.status == AgentStatus.RUNNING }
                        if (workspaces.isNotEmpty()) {
                            Text(
                                text = "$activeCount active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (workspaces.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = "No agents running",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Launch an AI agent to monitor it here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onLaunchAgent) {
                        Text("Launch Agent")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    items = workspaces,
                    key = { it.id },
                ) { workspace ->
                    AgentCard(
                        workspace = workspace,
                        onOpenSession = { onOpenSession(workspace) },
                        onPauseResume = { onPauseResume(workspace) },
                        onKill = {
                            workspaceToKill = workspace
                        },
                    )
                }

                // Bottom spacing for last card
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Kill confirmation dialog
    workspaceToKill?.let { target ->
        AlertDialog(
            onDismissRequest = { workspaceToKill = null },
            title = { Text("Kill agent?") },
            text = {
                Text("This will terminate \"${target.displayName}\" and clean up its workspace. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onKill(target)
                        workspaceToKill = null
                    },
                ) {
                    Text("Kill", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToKill = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}