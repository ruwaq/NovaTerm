package com.novaterm.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaterm.app.R
import com.novaterm.app.ui.theme.LocalNovaTermColors
import com.novaterm.terminal.TerminalSession

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionTabBar(
    sessions: List<TerminalSession>,
    selectedPage: Int,
    sessionNames: Map<Int, String>,
    onSelectTab: (Int) -> Unit,
    onLongClickTab: (Int) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewSession: () -> Unit,
    onAgentsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val novaColors = LocalNovaTermColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.cd_open_drawer),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }

        if (sessions.isNotEmpty()) {
            // key(sessions.size) forces full recomposition when tab count changes,
            // preventing PrimaryScrollableTabRow indicator from accessing a stale
            // selectedTabIndex during partial recomposition (IndexOutOfBoundsException).
            val clampedIndex = selectedPage.coerceIn(0, sessions.size - 1)
            key(sessions.size) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = clampedIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    edgePadding = 0.dp,
                    modifier = Modifier.weight(1f),
                    divider = {},
                ) {
                    sessions.forEachIndexed { index, session ->
                        Tab(
                            selected = clampedIndex == index,
                            onClick = { onSelectTab(index) },
                            modifier = Modifier
                                .height(48.dp)
                                .combinedClickable(
                                    onClick = { onSelectTab(index) },
                                    onLongClick = { onLongClickTab(index) },
                                    onLongClickLabel = "Rename session",
                                )
                                .semantics {
                                    stateDescription = if (session.isRunning) "Running" else "Stopped"
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            color = if (session.isRunning) novaColors.accent
                                            else novaColors.destructive,
                                            shape = CircleShape,
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
            }
        }

        IconButton(onClick = onSearchClick, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.action_search_history),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(onClick = onAgentsClick, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = "Agent Dashboard",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(onClick = onNewSession, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.action_new_session),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
