package com.novaterm.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.novaterm.feature.settings.R
import com.novaterm.feature.settings.model.AiTool
import com.novaterm.feature.settings.model.AuthMethod

/**
 * First-run AI tool setup screen.
 *
 * Shown after the color scheme picker, this screen lets users
 * select which AI coding tools to install and enter API keys.
 * It's opt-in — users can skip and set up later from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSetupScreen(
    onSkip: () -> Unit,
    onContinue: (selectedTools: List<AiTool>, apiKeys: Map<String, String>, updateFirst: Boolean) -> Unit,
) {
    val tools = AiTool.ALL
    val selectedTools = remember { mutableStateMapOf<String, Boolean>() }
    val apiKeys = remember { mutableStateMapOf<String, String>() }
    var updateFirst by remember { mutableStateOf(true) }

    // Initialize all tools as unselected
    LaunchedEffect(tools) {
        tools.forEach { tool ->
            if (tool.id !in selectedTools) {
                selectedTools[tool.id] = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_setup_title)) },
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.ai_setup_skip))
                    }
                    Button(
                        onClick = {
                            val selected = tools.filter { selectedTools[it.id] == true }
                            val keys = apiKeys.toMap().filterValues { it.isNotBlank() }
                            onContinue(selected, keys, updateFirst)
                        },
                    ) {
                        Text(stringResource(R.string.ai_setup_continue))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Tool cards
            tools.forEach { tool ->
                val isSelected = selectedTools[tool.id] ?: false
                AiSetupToolCard(
                    tool = tool,
                    isSelected = isSelected,
                    onToggle = { selectedTools[tool.id] = !isSelected },
                    apiKey = apiKeys[tool.id] ?: "",
                    onApiKeyChange = { apiKeys[tool.id] = it },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))

            // Update packages first checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = updateFirst,
                    onCheckedChange = { updateFirst = it },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_setup_update_system),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.ai_setup_update_system_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSetupToolCard(
    tool: AiTool,
    isSelected: Boolean,
    onToggle: () -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(tool.nameResId),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(tool.descResId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AuthMethodBadge(authMethod = tool.authMethod)
            }

            // Show dependencies hint when selected
            if (isSelected && tool.dependencies.isNotEmpty()) {
                Text(
                    text = "Requires: ${tool.dependencies.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                )
            }

            // Show post-install hint when selected
            if (isSelected && tool.postInstallHint != null) {
                Text(
                    text = tool.postInstallHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                )
            }

            // API key field for tools that need it
            if (isSelected && tool.authMethod == AuthMethod.API_KEY || tool.authMethod == AuthMethod.API_KEY_OR_LOCAL) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("${stringResource(tool.nameResId)} API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 8.dp),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun AuthMethodBadge(authMethod: AuthMethod) {
    val (textRes, containerColor) = when (authMethod) {
        AuthMethod.API_KEY -> R.string.ai_setup_auth_api_key to MaterialTheme.colorScheme.errorContainer
        AuthMethod.GOOGLE_LOGIN -> R.string.ai_setup_auth_google to MaterialTheme.colorScheme.tertiaryContainer
        AuthMethod.LOCAL_NO_KEY -> R.string.ai_setup_auth_local to MaterialTheme.colorScheme.primaryContainer
        AuthMethod.API_KEY_OR_LOCAL -> R.string.ai_setup_auth_api_or_local to MaterialTheme.colorScheme.secondaryContainer
        AuthMethod.FREE_TIER -> R.string.ai_setup_auth_free_tier to MaterialTheme.colorScheme.primaryContainer
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}