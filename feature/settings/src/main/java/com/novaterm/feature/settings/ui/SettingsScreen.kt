package com.novaterm.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novaterm.feature.settings.data.TerminalPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: TerminalPreferences,
    onPreferencesChanged: (TerminalPreferences) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Font size
            var fontSize by remember { mutableFloatStateOf(preferences.fontSize.toFloat()) }
            ListItem(
                headlineContent = { Text("Font size") },
                supportingContent = {
                    Column {
                        Text("${fontSize.toInt()} sp")
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            onValueChangeFinished = {
                                onPreferencesChanged(preferences.copy(fontSize = fontSize.toInt()))
                            },
                            valueRange = 8f..32f,
                            steps = 23,
                        )
                    }
                }
            )

            // Keep screen on
            ListItem(
                headlineContent = { Text("Keep screen on") },
                supportingContent = { Text("Prevent display from sleeping while terminal is active") },
                trailingContent = {
                    Switch(
                        checked = preferences.keepScreenOn,
                        onCheckedChange = {
                            onPreferencesChanged(preferences.copy(keepScreenOn = it))
                        }
                    )
                }
            )

            // Vibrate on key
            ListItem(
                headlineContent = { Text("Haptic feedback") },
                supportingContent = { Text("Vibrate on extra key press") },
                trailingContent = {
                    Switch(
                        checked = preferences.hapticFeedback,
                        onCheckedChange = {
                            onPreferencesChanged(preferences.copy(hapticFeedback = it))
                        }
                    )
                }
            )

            // Bell
            ListItem(
                headlineContent = { Text("Terminal bell") },
                supportingContent = { Text("Vibrate on bell character") },
                trailingContent = {
                    Switch(
                        checked = preferences.bellEnabled,
                        onCheckedChange = {
                            onPreferencesChanged(preferences.copy(bellEnabled = it))
                        }
                    )
                }
            )

            // Show extra keys
            ListItem(
                headlineContent = { Text("Extra keys bar") },
                supportingContent = { Text("Show shortcut keys above keyboard") },
                trailingContent = {
                    Switch(
                        checked = preferences.showExtraKeys,
                        onCheckedChange = {
                            onPreferencesChanged(preferences.copy(showExtraKeys = it))
                        }
                    )
                }
            )

            // Back button as ESC
            ListItem(
                headlineContent = { Text("Back button sends ESC") },
                supportingContent = { Text("Map hardware back button to Escape key") },
                trailingContent = {
                    Switch(
                        checked = preferences.backIsEscape,
                        onCheckedChange = {
                            onPreferencesChanged(preferences.copy(backIsEscape = it))
                        }
                    )
                }
            )
        }
    }
}
