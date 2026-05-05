package com.novaterm.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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
    onResetToDefaults: () -> Unit = {},
) {
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
            AppearanceSection(preferences, onPreferencesChanged)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            BehaviorSection(preferences, onPreferencesChanged)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            InputSection(preferences, onPreferencesChanged)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ExperimentalSection(preferences, onPreferencesChanged)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ResetSection(onResetToDefaults)
        }
    }
}

// ── Section composables ──────────────────────────────────────────────────────

@Composable
private fun AppearanceSection(
    preferences: TerminalPreferences,
    onPreferencesChanged: (TerminalPreferences) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_appearance))

    // Font size — local state for live preview while dragging
    var fontSize by remember(preferences.fontSize) { mutableFloatStateOf(preferences.fontSize.toFloat()) }
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

    // Font family
    var fontMenuExpanded by remember { mutableStateOf(false) }
    val currentFontLabel = TerminalPreferences.FONT_FAMILIES
        .firstOrNull { it.first == preferences.fontFamily }?.second ?: "System Monospace"
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_font_family)) },
        supportingContent = { Text(currentFontLabel) },
        modifier = Modifier.clickable { fontMenuExpanded = true }
    )
    DropdownMenu(expanded = fontMenuExpanded, onDismissRequest = { fontMenuExpanded = false }) {
        TerminalPreferences.FONT_FAMILIES.forEach { (id, displayName) ->
            DropdownMenuItem(
                text = { Text(displayName) },
                onClick = { onPreferencesChanged(preferences.copy(fontFamily = id)); fontMenuExpanded = false }
            )
        }
    }

    // Color scheme
    var schemeMenuExpanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_color_scheme)) },
        supportingContent = { Text(ColorSchemes.displayName(preferences.colorScheme)) },
        modifier = Modifier.clickable { schemeMenuExpanded = true }
    )
    DropdownMenu(expanded = schemeMenuExpanded, onDismissRequest = { schemeMenuExpanded = false }) {
        ColorSchemes.ALL.forEach { scheme ->
            DropdownMenuItem(
                text = { Text(scheme.displayName) },
                onClick = { onPreferencesChanged(preferences.copy(colorScheme = scheme.id)); schemeMenuExpanded = false }
            )
        }
    }
}

@Composable
private fun BehaviorSection(
    preferences: TerminalPreferences,
    onPreferencesChanged: (TerminalPreferences) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_behavior))
    ToggleSettingRow(
        title = stringResource(R.string.settings_keep_screen_on),
        subtitle = stringResource(R.string.settings_keep_screen_on_desc),
        checked = preferences.keepScreenOn,
        onCheckedChange = { onPreferencesChanged(preferences.copy(keepScreenOn = it)) },
    )
    ToggleSettingRow(
        title = stringResource(R.string.settings_haptic_feedback),
        subtitle = stringResource(R.string.settings_haptic_feedback_desc),
        checked = preferences.hapticFeedback,
        onCheckedChange = { onPreferencesChanged(preferences.copy(hapticFeedback = it)) },
    )
    ToggleSettingRow(
        title = stringResource(R.string.settings_bell),
        subtitle = stringResource(R.string.settings_bell_desc),
        checked = preferences.bellEnabled,
        onCheckedChange = { onPreferencesChanged(preferences.copy(bellEnabled = it)) },
    )
    ToggleSettingRow(
        title = "Session Grouping",
        subtitle = "Group sessions by project or type in the tab bar",
        checked = preferences.sessionGroupingEnabled,
        onCheckedChange = { onPreferencesChanged(preferences.copy(sessionGroupingEnabled = it)) },
    )

    // Scrollback buffer size
    var scrollbackMenuExpanded by remember { mutableStateOf(false) }
    val scrollbackLabel = java.text.NumberFormat.getIntegerInstance().format(preferences.scrollbackLines)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_scrollback)) },
        supportingContent = {
            Column {
                Text(
                    stringResource(R.string.settings_scrollback_value, scrollbackLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_scrollback_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable { scrollbackMenuExpanded = true },
    )
    DropdownMenu(expanded = scrollbackMenuExpanded, onDismissRequest = { scrollbackMenuExpanded = false }) {
        TerminalPreferences.SCROLLBACK_OPTIONS.forEach { lines ->
            val label = java.text.NumberFormat.getIntegerInstance().format(lines)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_scrollback_value, label)) },
                onClick = { onPreferencesChanged(preferences.copy(scrollbackLines = lines)); scrollbackMenuExpanded = false },
            )
        }
    }
}

@Composable
private fun InputSection(
    preferences: TerminalPreferences,
    onPreferencesChanged: (TerminalPreferences) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_input))
    ToggleSettingRow(
        title = stringResource(R.string.settings_extra_keys),
        subtitle = stringResource(R.string.settings_extra_keys_desc),
        checked = preferences.showExtraKeys,
        onCheckedChange = { onPreferencesChanged(preferences.copy(showExtraKeys = it)) },
    )
    if (preferences.showExtraKeys) {
        var styleMenuExpanded by remember { mutableStateOf(false) }
        val styleDisplayName = when (preferences.extraKeysStyle) {
            TerminalPreferences.EXTRA_KEYS_STYLE_VIM -> stringResource(R.string.settings_extra_keys_style_vim)
            TerminalPreferences.EXTRA_KEYS_STYLE_DEV -> stringResource(R.string.settings_extra_keys_style_dev)
            TerminalPreferences.EXTRA_KEYS_STYLE_MINIMAL -> stringResource(R.string.settings_extra_keys_style_minimal)
            else -> stringResource(R.string.settings_extra_keys_style_default)
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_extra_keys_style)) },
            supportingContent = { Text(styleDisplayName) },
            modifier = Modifier.clickable { styleMenuExpanded = true },
        )
        DropdownMenu(expanded = styleMenuExpanded, onDismissRequest = { styleMenuExpanded = false }) {
            ExtraKeysStyleOption(
                name = stringResource(R.string.settings_extra_keys_style_default),
                description = stringResource(R.string.settings_extra_keys_style_default_desc),
                onClick = { onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_DEFAULT)); styleMenuExpanded = false },
            )
            ExtraKeysStyleOption(
                name = stringResource(R.string.settings_extra_keys_style_vim),
                description = stringResource(R.string.settings_extra_keys_style_vim_desc),
                onClick = { onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_VIM)); styleMenuExpanded = false },
            )
            ExtraKeysStyleOption(
                name = stringResource(R.string.settings_extra_keys_style_dev),
                description = stringResource(R.string.settings_extra_keys_style_dev_desc),
                onClick = { onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_DEV)); styleMenuExpanded = false },
            )
            ExtraKeysStyleOption(
                name = stringResource(R.string.settings_extra_keys_style_minimal),
                description = stringResource(R.string.settings_extra_keys_style_minimal_desc),
                onClick = { onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_MINIMAL)); styleMenuExpanded = false },
            )
        }
    }
    ToggleSettingRow(
        title = stringResource(R.string.settings_back_is_escape),
        subtitle = stringResource(R.string.settings_back_is_escape_desc),
        checked = preferences.backIsEscape,
        onCheckedChange = { onPreferencesChanged(preferences.copy(backIsEscape = it)) },
    )
    ToggleSettingRow(
        title = stringResource(R.string.settings_pip_mode),
        subtitle = stringResource(R.string.settings_pip_mode_desc),
        checked = preferences.pipOnLeave,
        onCheckedChange = { onPreferencesChanged(preferences.copy(pipOnLeave = it)) },
    )
}

@Composable
private fun ExperimentalSection(
    preferences: TerminalPreferences,
    onPreferencesChanged: (TerminalPreferences) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_experimental))
    ToggleSettingRow(
        title = stringResource(R.string.settings_rust_backend),
        subtitle = stringResource(R.string.settings_rust_backend_desc),
        checked = preferences.useRustBackend,
        onCheckedChange = { onPreferencesChanged(preferences.copy(useRustBackend = it)) },
    )
    ToggleSettingRow(
        title = stringResource(R.string.settings_gpu_renderer),
        subtitle = stringResource(R.string.settings_gpu_renderer_desc),
        checked = preferences.useGpuRenderer,
        onCheckedChange = { onPreferencesChanged(preferences.copy(useGpuRenderer = it)) },
    )
}

@Composable
private fun ResetSection(onResetToDefaults: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text(stringResource(R.string.settings_reset_title))
    }
    Text(
        text = stringResource(R.string.settings_reset_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Spacer(modifier = Modifier.height(16.dp))

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onResetToDefaults(); showDialog = false }) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.settings_reset_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

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
                onCheckedChange = null,
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun ExtraKeysStyleOption(
    name: String,
    description: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(name)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}
