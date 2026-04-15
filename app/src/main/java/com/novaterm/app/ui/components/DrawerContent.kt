package com.novaterm.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novaterm.app.R
import com.novaterm.app.ui.theme.LocalNovaTermColors
import com.novaterm.core.session.manager.GroupWithCount
import com.novaterm.core.session.persistence.SessionGroup

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(
    sessionCount: Int,
    selectedIndex: Int,
    onSelectSession: (Int) -> Unit,
    onCloseSession: (Int) -> Unit,
    onNewSession: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    groups: List<GroupWithCount> = emptyList(),
    sessionGroupMap: Map<Int, String> = emptyMap(),
    groupingEnabled: Boolean = false,
    onMoveSessionToGroup: ((Int, String) -> Unit)? = null,
) {
    val novaColors = LocalNovaTermColors.current

    ModalDrawerSheet {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.headlineSmall,
        )

        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        if (groupingEnabled && groups.isNotEmpty()) {
            // ── Grouped session list ──────────────────────────────────
            GroupedSessionList(
                groups = groups,
                sessionGroupMap = sessionGroupMap,
                selectedIndex = selectedIndex,
                onSelectSession = onSelectSession,
                onCloseSession = onCloseSession,
                onMoveSessionToGroup = onMoveSessionToGroup,
            )
        } else {
            // ── Flat session list (fallback) ────────────────────────
            FlatSessionList(
                sessionCount = sessionCount,
                selectedIndex = selectedIndex,
                onSelectSession = onSelectSession,
                onCloseSession = onCloseSession,
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
            icon = { Icon(Icons.Default.Info, contentDescription = stringResource(R.string.cd_about)) },
            label = { Text(stringResource(R.string.about_title)) },
            selected = false,
            onClick = onAbout,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun FlatSessionList(
    sessionCount: Int,
    selectedIndex: Int,
    onSelectSession: (Int) -> Unit,
    onCloseSession: (Int) -> Unit,
) {
    repeat(sessionCount) { index ->
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.tab_session, index + 1)) },
            selected = index == selectedIndex,
            onClick = { onSelectSession(index) },
            badge = {
                IconButton(
                    onClick = { onCloseSession(index) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close_session),
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun GroupedSessionList(
    groups: List<GroupWithCount>,
    sessionGroupMap: Map<Int, String>,
    selectedIndex: Int,
    onSelectSession: (Int) -> Unit,
    onCloseSession: (Int) -> Unit,
    onMoveSessionToGroup: ((Int, String) -> Unit)?,
) {
    val novaColors = LocalNovaTermColors.current

    groups.forEach { (group, count) ->
        val groupColor = parseDrawerColor(group.color)
        val sessionsInGroup = sessionGroupMap.entries
            .filter { (_, gid) -> gid == group.id }
            .sortedBy { it.key }

        // Group header
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .then(
                        Modifier.background(
                            color = groupColor,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                    ),
            )
            Text(
                text = group.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = groupColor,
            )
            if (count > 1) {
                Text(
                    text = "($count)",
                    style = MaterialTheme.typography.labelSmall,
                    color = groupColor.copy(alpha = 0.7f),
                )
            }
        }

        // Sessions in this group
        sessionsInGroup.forEach { (sessionIdx, _) ->
            key(sessionIdx) {
                var contextMenuExpanded by remember { mutableStateOf(false) }

                NavigationDrawerItem(
                label = { Text(stringResource(R.string.tab_session, sessionIdx + 1)) },
                selected = sessionIdx == selectedIndex,
                onClick = { onSelectSession(sessionIdx) },
                badge = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Move to group button (only if callback provided)
                        if (onMoveSessionToGroup != null && groups.size > 1) {
                            IconButton(
                                onClick = { contextMenuExpanded = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.DriveFileMove,
                                    contentDescription = "Move to group",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = contextMenuExpanded,
                                onDismissRequest = { contextMenuExpanded = false },
                            ) {
                                groups
                                    .filter { (g, _) -> g.id != group.id }
                                    .forEach { (targetGroup, _) ->
                                        DropdownMenuItem(
                                            text = { Text(targetGroup.name) },
                                            onClick = {
                                                onMoveSessionToGroup(sessionIdx, targetGroup.id)
                                                contextMenuExpanded = false
                                            },
                                        )
                                    }
                            }
                        }
                        // Close button
                        IconButton(
                            onClick = { onCloseSession(sessionIdx) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close_session),
                                tint = novaColors.destructive,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            }
        }
    }
}

/** Parse hex color for drawer, falls back to Ember orange. */
private fun parseDrawerColor(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val colorInt = cleaned.toLong(16)
        Color(0xFF000000 or colorInt)
    } catch (_: Exception) {
        Color(0xFFE85D04)
    }
}