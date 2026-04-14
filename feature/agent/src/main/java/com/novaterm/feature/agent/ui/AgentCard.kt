package com.novaterm.feature.agent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novaterm.core.session.manager.AgentStatus
import com.novaterm.core.session.manager.AgentType
import com.novaterm.core.session.manager.AgentWorkspace
import kotlinx.coroutines.delay

/** Color mapping for agent types, matching AgentPresetSheet. */
val AgentType.cardColor: Color
    get() = when (this) {
        AgentType.CLAUDE_CODE -> Color(0xFFD97706) // Amber
        AgentType.GEMINI_CLI -> Color(0xFF4285F4) // Google Blue
        AgentType.AIDER -> Color(0xFF10B981)       // Emerald
        AgentType.OPENCODE -> Color(0xFF8B5CF6)    // Violet
        AgentType.CUSTOM -> Color(0xFF6B7280)      // Gray
    }

/** Status badge color. */
val AgentStatus.badgeColor: Color
    get() = when (this) {
        AgentStatus.IDLE -> Color(0xFF9CA3AF)        // Gray
        AgentStatus.RUNNING -> Color(0xFF22C55E)      // Green
        AgentStatus.PAUSED -> Color(0xFFEAB308)       // Yellow
        AgentStatus.COMPLETED -> Color(0xFF3B82F6)    // Blue
        AgentStatus.ERROR -> Color(0xFFEF4444)         // Red
        AgentStatus.DESTROYED -> Color(0xFF6B7280)    // Gray
    }

/** Human-readable status label. */
val AgentStatus.label: String
    get() = when (this) {
        AgentStatus.IDLE -> "Idle"
        AgentStatus.RUNNING -> "Running"
        AgentStatus.PAUSED -> "Paused"
        AgentStatus.COMPLETED -> "Completed"
        AgentStatus.ERROR -> "Error"
        AgentStatus.DESTROYED -> "Destroyed"
    }

/** Format runtime in seconds to a human-readable string. */
fun formatRuntime(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${secs}s"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

/**
 * Card displaying an agent workspace's status, runtime, and action buttons.
 */
@Composable
fun AgentCard(
    workspace: AgentWorkspace,
    onOpenSession: () -> Unit,
    onPauseResume: () -> Unit,
    onKill: () -> Unit,
    onViewDiff: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Live runtime counter for RUNNING workspaces
    var runtimeNow by remember(workspace.startedAt, workspace.status) {
        mutableLongStateOf(workspace.runtimeSeconds)
    }
    LaunchedEffect(workspace.status, workspace.startedAt) {
        if (workspace.status == AgentStatus.RUNNING) {
            while (true) {
                runtimeNow = (System.currentTimeMillis() - workspace.startedAt) / 1000
                delay(1000)
            }
        } else {
            runtimeNow = workspace.runtimeSeconds
        }
    }

    Card(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Color dot + Name + Status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Agent type color dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(workspace.agentType.cardColor),
                )

                Text(
                    text = workspace.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                // Status badge
                val badgeColor by animateColorAsState(
                    targetValue = workspace.status.badgeColor,
                    label = "status-badge",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                    )
                    Text(
                        text = workspace.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = badgeColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Runtime + PID + working directory
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = formatRuntime(runtimeNow),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (workspace.pid > 0) {
                    Text(
                        text = "PID ${workspace.pid}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (workspace.workingDir.isNotEmpty()) {
                Text(
                    text = workspace.workingDir.substringAfterLast("/"),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Open session
                IconButton(
                    onClick = onOpenSession,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open session",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // View diff
                if (workspace.status == AgentStatus.COMPLETED || workspace.status == AgentStatus.RUNNING || workspace.status == AgentStatus.PAUSED) {
                    IconButton(
                        onClick = onViewDiff,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Difference,
                            contentDescription = "View diff",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Pause / Resume
                if (workspace.status == AgentStatus.RUNNING || workspace.status == AgentStatus.PAUSED) {
                    IconButton(
                        onClick = onPauseResume,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (workspace.status == AgentStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (workspace.status == AgentStatus.RUNNING) "Pause" else "Resume",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Kill
                if (workspace.status == AgentStatus.RUNNING || workspace.status == AgentStatus.PAUSED || workspace.status == AgentStatus.IDLE) {
                    IconButton(
                        onClick = onKill,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Kill",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}