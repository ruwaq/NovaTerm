package com.novaterm.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaterm.app.R
import com.novaterm.app.ui.theme.LocalNovaTermColors
import com.novaterm.core.session.persistence.SessionGroup
import com.novaterm.core.session.manager.GroupWithCount
import com.novaterm.terminal.TerminalSession

/**
 * Grouped session tab bar — the Superset IDE-inspired navigation pattern.
 *
 * Sessions are organized into collapsible groups. Each group has a colored
 * header showing the group name, icon, and session count. When expanded,
 * the group's sessions appear as tabs below the header.
 *
 * When grouping is disabled, falls back to a flat [SessionTabBar].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupedSessionTabBar(
    sessions: List<TerminalSession>,
    selectedPage: Int,
    sessionNames: Map<Int, String>,
    groups: List<GroupWithCount>,
    sessionGroupMap: Map<Int, String>,
    groupingEnabled: Boolean,
    onSelectTab: (Int) -> Unit,
    onLongClickTab: (Int) -> Unit,
    onToggleGroup: (String) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewSession: () -> Unit,
    onAgentsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // When grouping is disabled, fall back to flat tab bar
    if (!groupingEnabled || groups.isEmpty()) {
        SessionTabBar(
            sessions = sessions,
            selectedPage = selectedPage,
            sessionNames = sessionNames,
            onSelectTab = onSelectTab,
            onLongClickTab = onLongClickTab,
            onMenuClick = onMenuClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onNewSession = onNewSession,
            onAgentsClick = onAgentsClick,
            modifier = modifier,
        )
        return
    }

    val novaColors = LocalNovaTermColors.current
    val safeIndex = selectedPage.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // ── Group headers row ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Menu button
            IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.cd_open_drawer),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Group headers as compact chips
            groups.forEach { (group, count) ->
                val groupColor = parseColor(group.color)
                val isGroupSelected = sessionGroupMap[safeIndex] == group.id

                GroupChip(
                    group = group,
                    sessionCount = count,
                    isSelected = isGroupSelected,
                    accentColor = groupColor,
                    onClick = { onToggleGroup(group.id) },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }

            // Spacer to push action buttons right
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.action_search_history),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(onClick = onAgentsClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "Agent Dashboard",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(onClick = onNewSession, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_new_session),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // ── Expanded group tabs ────────────────────────────────────
        // Show tabs for the expanded groups (sessions in those groups)
        val expandedGroupIds = groups
            .filter { (group, _) -> group.isExpanded }
            .map { (group, _) -> group.id }

        if (expandedGroupIds.isNotEmpty() && sessions.isNotEmpty()) {
            // Collect all sessions in expanded groups, in group order
            val expandedSessionIndices = mutableListOf<Int>()
            val sessionToGroup = mutableMapOf<Int, String>()

            for ((group, _) in groups) {
                if (group.id in expandedGroupIds) {
                    sessionGroupMap.forEach { (idx, gid) ->
                        if (gid == group.id && idx in sessions.indices) {
                            expandedSessionIndices.add(idx)
                            sessionToGroup[idx] = group.id
                        }
                    }
                }
            }

            // Also include sessions without a group assignment (ungrouped)
            // They go into the first expanded group or "shell" if it's expanded
            sessions.indices.forEach { idx ->
                if (idx !in sessionGroupMap && idx !in expandedSessionIndices) {
                    // Assign to shell group if expanded, else skip
                    if ("shell" in expandedGroupIds) {
                        expandedSessionIndices.add(idx)
                        sessionToGroup[idx] = "shell"
                    }
                }
            }

            if (expandedSessionIndices.isNotEmpty()) {
                val clampedIndex = if (safeIndex in expandedSessionIndices) {
                    expandedSessionIndices.indexOf(safeIndex).coerceAtLeast(0)
                } else {
                    0
                }

                key(expandedSessionIndices.size) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = clampedIndex,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        edgePadding = 0.dp,
                        divider = {},
                    ) {
                        expandedSessionIndices.forEach { sessionIdx ->
                            key(sessionIdx) {
                                val groupColor = parseColor(
                                    groups.find { (g, _) -> g.id == sessionToGroup[sessionIdx] }
                                        ?.group?.color ?: SessionGroup.DEFAULT_COLOR
                                )
                                val isTabSelected = safeIndex == sessionIdx

                                Tab(
                                    selected = isTabSelected,
                                    onClick = { onSelectTab(sessionIdx) },
                                    modifier = Modifier
                                        .height(36.dp)
                                        .combinedClickable(
                                            onClick = { onSelectTab(sessionIdx) },
                                            onLongClick = { onLongClickTab(sessionIdx) },
                                            onLongClickLabel = "Rename session",
                                        )
                                        .semantics {
                                            stateDescription = if (sessions[sessionIdx].isRunning) "Running" else "Stopped"
                                        },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        // Status dot tinted with group color
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    color = if (sessions[sessionIdx].isRunning) groupColor
                                                    else novaColors.destructive,
                                                    shape = CircleShape,
                                                )
                                        )
                                        Text(
                                            text = sessionNames[sessionIdx]
                                                ?: sessions.getOrNull(sessionIdx)?.title?.takeIf { it.isNotBlank() }
                                                ?: stringResource(R.string.tab_session, sessionIdx + 1),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A compact chip representing a session group in the tab bar header.
 * Shows group name, status indicator, and expand/collapse icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(
    group: SessionGroup,
    sessionCount: Int,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) {
        accentColor.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Group color indicator
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(accentColor, CircleShape),
        )

        Text(
            text = group.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Session count badge (only if > 1)
        if (sessionCount > 1) {
            Text(
                text = "$sessionCount",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
            )
        }

        // Expand/collapse indicator
        Icon(
            imageVector = if (group.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (group.isExpanded) "Collapse" else "Expand",
            tint = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Parse a hex color string to a Compose [Color].
 * Falls back to [SessionGroup.DEFAULT_COLOR] on parse failure.
 */
private fun parseColor(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val colorInt = cleaned.toLong(16)
        Color(0xFF000000 or colorInt)
    } catch (_: Exception) {
        Color(0xFFE85D04) // Fallback to Ember orange
    }
}