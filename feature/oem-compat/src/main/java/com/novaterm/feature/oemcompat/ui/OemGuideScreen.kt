package com.novaterm.feature.oemcompat.ui

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novaterm.feature.oemcompat.R
import com.novaterm.feature.oemcompat.detection.OemInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemGuideScreen(
    oemInfo: OemInfo,
    needsWhitelist: Boolean,
    instructions: List<String>,
    onRequestBatteryExemption: () -> Unit,
    onOpenAutostartSettings: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.oem_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.oem_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Status card ────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (needsWhitelist)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            if (needsWhitelist) stringResource(R.string.oem_action_required)
                            else stringResource(R.string.oem_all_set)
                        )
                    },
                    supportingContent = {
                        Text(
                            if (needsWhitelist)
                                stringResource(R.string.oem_warning_desc, oemInfo.brand.displayName)
                            else
                                stringResource(R.string.oem_ok_desc)
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (needsWhitelist) Icons.Default.Warning
                            else Icons.Default.CheckCircle,
                            contentDescription = if (needsWhitelist)
                                stringResource(R.string.oem_icon_warning)
                            else
                                stringResource(R.string.oem_icon_ok),
                        )
                    }
                )
            }

            if (needsWhitelist) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRequestBatteryExemption,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.oem_disable_optimization))
                }

                if (onOpenAutostartSettings != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenAutostartSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.oem_open_autostart))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.oem_steps_for, oemInfo.brand.displayName),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                instructions.forEachIndexed { index, instruction ->
                    ListItem(
                        headlineContent = { Text(instruction) },
                        leadingContent = {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.oem_device_label, oemInfo.manufacturer, oemInfo.model),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.oem_android_label, oemInfo.androidVersionName, oemInfo.androidVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
