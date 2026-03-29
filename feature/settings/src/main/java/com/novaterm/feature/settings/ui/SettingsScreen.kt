package com.novaterm.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novaterm.core.common.model.ColorSchemes
import com.novaterm.feature.settings.R
import com.novaterm.feature.settings.data.TerminalPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: TerminalPreferences,
    onPreferencesChanged: (TerminalPreferences) -> Unit,
    onBack: () -> Unit,
) {
    // Local slider state that follows external preference changes.
    var fontSize by remember(preferences.fontSize) {
        mutableFloatStateOf(preferences.fontSize.toFloat())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
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
            // ── Appearance ─────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_appearance))

            // Font size
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_font_size)) },
                supportingContent = {
                    Column {
                        Text(
                            stringResource(R.string.settings_font_size_value, fontSize.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = fontSize,
                            onValueChange = {
                                fontSize = it
                                // Live preview: update immediately while dragging
                                onPreferencesChanged(preferences.copy(fontSize = it.toInt()))
                            },
                            valueRange = 8f..32f,
                            steps = 23,
                        )
                    }
                }
            )

            // Color scheme
            var schemeMenuExpanded by remember { mutableStateOf(false) }
            val currentSchemeLabel = ColorSchemes.displayName(preferences.colorScheme)

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_color_scheme)) },
                supportingContent = { Text(currentSchemeLabel) },
                modifier = Modifier.clickable { schemeMenuExpanded = true }
            )
            DropdownMenu(
                expanded = schemeMenuExpanded,
                onDismissRequest = { schemeMenuExpanded = false }
            ) {
                ColorSchemes.ALL.forEach { scheme ->
                    DropdownMenuItem(
                        text = { Text(scheme.displayName) },
                        onClick = {
                            onPreferencesChanged(preferences.copy(colorScheme = scheme.id))
                            schemeMenuExpanded = false
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Behavior ───────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_behavior))

            // Keep screen on
            ToggleSettingRow(
                title = stringResource(R.string.settings_keep_screen_on),
                subtitle = stringResource(R.string.settings_keep_screen_on_desc),
                checked = preferences.keepScreenOn,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(keepScreenOn = it))
                },
            )

            // Haptic feedback
            ToggleSettingRow(
                title = stringResource(R.string.settings_haptic_feedback),
                subtitle = stringResource(R.string.settings_haptic_feedback_desc),
                checked = preferences.hapticFeedback,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(hapticFeedback = it))
                },
            )

            // Terminal bell
            ToggleSettingRow(
                title = stringResource(R.string.settings_bell),
                subtitle = stringResource(R.string.settings_bell_desc),
                checked = preferences.bellEnabled,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(bellEnabled = it))
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Input ──────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_input))

            // Extra keys bar
            ToggleSettingRow(
                title = stringResource(R.string.settings_extra_keys),
                subtitle = stringResource(R.string.settings_extra_keys_desc),
                checked = preferences.showExtraKeys,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(showExtraKeys = it))
                },
            )

            // Back button as ESC
            ToggleSettingRow(
                title = stringResource(R.string.settings_back_is_escape),
                subtitle = stringResource(R.string.settings_back_is_escape_desc),
                checked = preferences.backIsEscape,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(backIsEscape = it))
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Experimental ──────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_experimental))

            // Rust VT backend
            ToggleSettingRow(
                title = stringResource(R.string.settings_rust_backend),
                subtitle = stringResource(R.string.settings_rust_backend_desc),
                checked = preferences.useRustBackend,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(useRustBackend = it))
                },
            )

            // GPU renderer
            ToggleSettingRow(
                title = stringResource(R.string.settings_gpu_renderer),
                subtitle = stringResource(R.string.settings_gpu_renderer_desc),
                checked = preferences.useGpuRenderer,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(useGpuRenderer = it))
                },
            )
        }
    }
}

/** Section header label inside the settings list. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * A settings row where tapping anywhere in the row toggles the switch,
 * not just tapping the tiny switch widget.
 */
@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null, // Handled by the row click.
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}
