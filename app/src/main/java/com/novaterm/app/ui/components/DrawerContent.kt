package com.novaterm.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novaterm.app.R
import com.novaterm.app.ui.theme.LocalNovaTermColors

@Composable
fun DrawerContent(
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
                label = { Text(stringResource(R.string.tab_session, index + 1)) },
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
            icon = { Icon(Icons.Default.Info, contentDescription = stringResource(R.string.cd_about)) },
            label = { Text(stringResource(R.string.about_title)) },
            selected = false,
            onClick = onAbout,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
