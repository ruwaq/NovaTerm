package com.novaterm.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaterm.core.common.model.ColorSchemes
import com.novaterm.core.llm.ModelCatalog
import com.novaterm.core.llm.ModelState
import com.novaterm.feature.settings.R
import com.novaterm.feature.settings.data.TerminalPreferences
import com.novaterm.feature.settings.model.AiTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: TerminalPreferences,
    onPreferencesChanged: (TerminalPreferences) -> Unit,
    onBack: () -> Unit,
    onResetToDefaults: () -> Unit = {},
    modelState: ModelState = ModelState.Idle,
    onDownloadModel: (String) -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onDeleteModel: () -> Unit = {},
    onInstallAiTool: (String) -> Unit = {},
    mcpAuthToken: String? = null,
) {
    // Local slider state that follows external preference changes.
    var fontSize by remember(preferences.fontSize) {
        mutableFloatStateOf(preferences.fontSize.toFloat())
    }

    // Reset confirmation dialog state
    var showResetDialog by remember { mutableStateOf(false) }

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

            // Font family
            var fontMenuExpanded by remember { mutableStateOf(false) }
            val currentFontLabel = TerminalPreferences.FONT_FAMILIES
                .firstOrNull { it.first == preferences.fontFamily }?.second ?: "System Monospace"

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_font_family)) },
                supportingContent = { Text(currentFontLabel) },
                modifier = Modifier.clickable { fontMenuExpanded = true }
            )
            DropdownMenu(
                expanded = fontMenuExpanded,
                onDismissRequest = { fontMenuExpanded = false }
            ) {
                TerminalPreferences.FONT_FAMILIES.forEach { (id, displayName) ->
                    DropdownMenuItem(
                        text = { Text(displayName) },
                        onClick = {
                            onPreferencesChanged(preferences.copy(fontFamily = id))
                            fontMenuExpanded = false
                        }
                    )
                }
            }

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

            // Scrollback buffer size
            var scrollbackMenuExpanded by remember { mutableStateOf(false) }
            val scrollbackLabel = java.text.NumberFormat.getIntegerInstance()
                .format(preferences.scrollbackLines)

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
            DropdownMenu(
                expanded = scrollbackMenuExpanded,
                onDismissRequest = { scrollbackMenuExpanded = false },
            ) {
                TerminalPreferences.SCROLLBACK_OPTIONS.forEach { lines ->
                    val label = java.text.NumberFormat.getIntegerInstance().format(lines)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_scrollback_value, label)) },
                        onClick = {
                            onPreferencesChanged(preferences.copy(scrollbackLines = lines))
                            scrollbackMenuExpanded = false
                        },
                    )
                }
            }

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

            // Extra keys style (only shown when extra keys are enabled)
            if (preferences.showExtraKeys) {
                var styleMenuExpanded by remember { mutableStateOf(false) }
                val styleDisplayName = when (preferences.extraKeysStyle) {
                    TerminalPreferences.EXTRA_KEYS_STYLE_VIM -> stringResource(R.string.settings_extra_keys_style_vim)
                    TerminalPreferences.EXTRA_KEYS_STYLE_DEV -> stringResource(R.string.settings_extra_keys_style_dev)
                    TerminalPreferences.EXTRA_KEYS_STYLE_MINIMAL -> stringResource(R.string.settings_extra_keys_style_minimal)
                    TerminalPreferences.EXTRA_KEYS_STYLE_AI -> stringResource(R.string.settings_extra_keys_style_ai)
                    else -> stringResource(R.string.settings_extra_keys_style_default)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_extra_keys_style)) },
                    supportingContent = { Text(styleDisplayName) },
                    modifier = Modifier.clickable { styleMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = styleMenuExpanded,
                    onDismissRequest = { styleMenuExpanded = false },
                ) {
                    ExtraKeysStyleOption(
                        name = stringResource(R.string.settings_extra_keys_style_default),
                        description = stringResource(R.string.settings_extra_keys_style_default_desc),
                        onClick = {
                            onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_DEFAULT))
                            styleMenuExpanded = false
                        },
                    )
                    ExtraKeysStyleOption(
                        name = stringResource(R.string.settings_extra_keys_style_vim),
                        description = stringResource(R.string.settings_extra_keys_style_vim_desc),
                        onClick = {
                            onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_VIM))
                            styleMenuExpanded = false
                        },
                    )
                    ExtraKeysStyleOption(
                        name = stringResource(R.string.settings_extra_keys_style_dev),
                        description = stringResource(R.string.settings_extra_keys_style_dev_desc),
                        onClick = {
                            onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_DEV))
                            styleMenuExpanded = false
                        },
                    )
                    ExtraKeysStyleOption(
                        name = stringResource(R.string.settings_extra_keys_style_minimal),
                        description = stringResource(R.string.settings_extra_keys_style_minimal_desc),
                        onClick = {
                            onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_MINIMAL))
                            styleMenuExpanded = false
                        },
                    )
                    ExtraKeysStyleOption(
                        name = stringResource(R.string.settings_extra_keys_style_ai),
                        description = stringResource(R.string.settings_extra_keys_style_ai_desc),
                        onClick = {
                            onPreferencesChanged(preferences.copy(extraKeysStyle = TerminalPreferences.EXTRA_KEYS_STYLE_AI))
                            styleMenuExpanded = false
                        },
                    )
                }
            }

            // Back button as ESC
            ToggleSettingRow(
                title = stringResource(R.string.settings_back_is_escape),
                subtitle = stringResource(R.string.settings_back_is_escape_desc),
                checked = preferences.backIsEscape,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(backIsEscape = it))
                },
            )

            // Picture-in-Picture on leave
            ToggleSettingRow(
                title = stringResource(R.string.settings_pip_mode),
                subtitle = stringResource(R.string.settings_pip_mode_desc),
                checked = preferences.pipOnLeave,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(pipOnLeave = it))
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

            // MCP server
            ToggleSettingRow(
                title = stringResource(R.string.settings_mcp_server),
                subtitle = stringResource(R.string.settings_mcp_server_desc),
                checked = preferences.mcpEnabled,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(mcpEnabled = it))
                },
            )

            // MCP port and token (only show when MCP is enabled)
            if (preferences.mcpEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_mcp_port)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_mcp_port_value, preferences.mcpPort),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                // MCP auth token (read-only, with copy and reveal toggle)
                if (mcpAuthToken != null) {
                    McpTokenRow(token = mcpAuthToken)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── AI ────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_ai))

            // On-device LLM toggle
            ToggleSettingRow(
                title = stringResource(R.string.settings_llm_enabled),
                subtitle = stringResource(R.string.settings_llm_enabled_desc),
                checked = preferences.llmEnabled,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(llmEnabled = it))
                },
            )

            // Model download/status — inline progress (Google Translate pattern)
            if (preferences.llmEnabled) {
                ModelDownloadRow(
                    state = modelState,
                    onDownload = onDownloadModel,
                    onCancel = onCancelDownload,
                    onDelete = onDeleteModel,
                )

                // Model selector (expandable list of available models)
                if (modelState is ModelState.NotDownloaded || modelState is ModelState.Error) {
                    var modelMenuExpanded by remember { mutableStateOf(false) }
                    val currentModel = when (modelState) {
                        is ModelState.NotDownloaded -> modelState.displayName
                        else -> ModelCatalog.DEFAULT.displayName
                    }

                    ListItem(
                        headlineContent = { Text("Model") },
                        supportingContent = { Text(currentModel) },
                        modifier = Modifier.clickable { modelMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                    ) {
                        ModelCatalog.ALL.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.displayName)
                                        Text(
                                            "${model.sizeMb} MB · ${model.ramMb} MB RAM",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    onDownloadModel(model.id)
                                    modelMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // ── AI Tools ─────────────────────────────────────
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(stringResource(R.string.settings_ai_tools))
            Text(
                text = stringResource(R.string.settings_ai_tools_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            AiTool.ALL.forEach { tool ->
                AiToolRow(
                    tool = tool,
                    onInstall = { onInstallAiTool(tool.installCommand) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── AI API Keys ──────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_api_keys))

            ApiKeyField(
                label = stringResource(R.string.settings_api_key_anthropic),
                value = preferences.anthropicApiKey,
                onValueChange = { onPreferencesChanged(preferences.copy(anthropicApiKey = it)) },
                onClear = { onPreferencesChanged(preferences.copy(anthropicApiKey = "")) },
            )

            ApiKeyField(
                label = stringResource(R.string.settings_api_key_google),
                value = preferences.googleApiKey,
                onValueChange = { onPreferencesChanged(preferences.copy(googleApiKey = it)) },
                onClear = { onPreferencesChanged(preferences.copy(googleApiKey = "")) },
            )

            ApiKeyField(
                label = stringResource(R.string.settings_api_key_openai),
                value = preferences.openaiApiKey,
                onValueChange = { onPreferencesChanged(preferences.copy(openaiApiKey = it)) },
                onClear = { onPreferencesChanged(preferences.copy(openaiApiKey = "")) },
            )

            ApiKeyField(
                label = stringResource(R.string.settings_api_key_openrouter),
                value = preferences.openrouterApiKey,
                onValueChange = { onPreferencesChanged(preferences.copy(openrouterApiKey = it)) },
                onClear = { onPreferencesChanged(preferences.copy(openrouterApiKey = "")) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Reset ─────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.settings_reset_cancel))
                }
            },
        )
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

/** Inline model download row — shows progress, status, and actions (Google Translate pattern). */
@Composable
private fun ModelDownloadRow(
    state: ModelState,
    onDownload: (String) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                when (state) {
                    is ModelState.NotDownloaded -> state.displayName
                    is ModelState.Downloading -> state.displayName
                    is ModelState.Ready -> state.displayName
                    is ModelState.Error -> "Download failed"
                    else -> "AI Model"
                }
            )
        },
        supportingContent = {
            Column {
                when (state) {
                    is ModelState.NotDownloaded -> {
                        Text(
                            "${state.sizeMb} MB download · ${state.ramMb} MB RAM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is ModelState.Downloading -> {
                        Text(
                            "${state.downloadedMb} / ${state.totalMb} MB · ${state.progressPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is ModelState.Ready -> {
                        Text(
                            "Ready · ${state.sizeMb} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is ModelState.Error -> {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> {}
                }
            }
        },
        trailingContent = {
            when (state) {
                is ModelState.NotDownloaded -> {
                    IconButton(onClick = { onDownload(state.modelId) }) {
                        Icon(Icons.Outlined.Download, contentDescription = "Download model")
                    }
                }
                is ModelState.Downloading -> {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel download")
                    }
                }
                is ModelState.Ready -> {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete model")
                    }
                }
                is ModelState.Error -> {
                    IconButton(onClick = { onDownload(ModelCatalog.DEFAULT.id) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Retry download")
                    }
                }
                else -> {}
            }
        },
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

/**
 * Visual transformation that masks all characters except the last 4.
 * "sk-abc123xyz" → "••••••••xyz" (dots for all but last 4).
 */
private class ApiKeyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val masked = if (original.length <= 4) {
            original
        } else {
            "●".repeat(original.length - 4) + original.takeLast(4)
        }
        return TransformedText(
            AnnotatedString(masked),
            OffsetMapping.Identity,
        )
    }
}

/** A row displaying an AI tool with an Install button. */
@Composable
private fun AiToolRow(
    tool: AiTool,
    onInstall: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(tool.nameResId)) },
        supportingContent = {
            Text(
                stringResource(tool.descResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            OutlinedButton(onClick = onInstall) {
                Text(stringResource(R.string.settings_install))
            }
        },
    )
}

/** A text field for entering and displaying a masked API key with a clear button. */
@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // Local editing state — only propagate on done/focus loss
    var editingValue by remember(value) { mutableStateOf(value) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = editingValue,
            onValueChange = { editingValue = it },
            label = { Text(label) },
            placeholder = {
                Text(
                    if (value.isEmpty()) stringResource(R.string.settings_api_key_not_set)
                    else stringResource(R.string.settings_api_key_hint)
                )
            },
            visualTransformation = if (editingValue.isNotEmpty()) ApiKeyVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onValueChange(editingValue)
                    focusManager.clearFocus()
                }
            ),
            trailingIcon = {
                if (editingValue.isNotEmpty()) {
                    IconButton(onClick = {
                        editingValue = ""
                        onClear()
                    }) {
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = stringResource(R.string.settings_api_key_clear),
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A dropdown menu item showing a style name and short description. */
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

/** Read-only row displaying the MCP bearer token with copy and reveal toggle. */
@Composable
private fun McpTokenRow(token: String) {
    var revealed by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val displayText = if (revealed) token else "****${token.takeLast(8)}"

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_mcp_token)) },
        supportingContent = {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Reveal / hide toggle
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) Icons.Outlined.VisibilityOff
                        else Icons.Outlined.Visibility,
                        contentDescription = if (revealed)
                            stringResource(R.string.settings_mcp_token_hide)
                        else stringResource(R.string.settings_mcp_token_reveal),
                        modifier = Modifier.size(20.dp),
                    )
                }
                // Copy to clipboard
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(token))
                }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.settings_mcp_token_copy),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}
