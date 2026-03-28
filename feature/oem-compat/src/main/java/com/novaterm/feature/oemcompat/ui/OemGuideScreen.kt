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
import androidx.compose.ui.unit.dp
import com.novaterm.feature.oemcompat.detection.OemInfo

/**
 * Guide screen that shows battery-optimization instructions specific to the
 * user's device brand.
 *
 * All business logic results (instructions, whether whitelist is needed) are
 * passed in as parameters so that the composable is pure UI and testable
 * in @Preview without a real [android.content.Context].
 *
 * @param oemInfo                   Detected device info.
 * @param needsWhitelist            Whether the user still needs to disable battery optimization.
 * @param instructions              Step-by-step instructions for this brand.
 * @param onRequestBatteryExemption Launches the system battery-exemption dialog.
 * @param onOpenAutostartSettings   Opens the OEM autostart activity (null if unavailable).
 * @param onBack                    Navigate back.
 */
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
                title = { Text("Battery Optimization") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            if (needsWhitelist) "Action required"
                            else "You're all set"
                        )
                    },
                    supportingContent = {
                        Text(
                            if (needsWhitelist)
                                "${oemInfo.brand.displayName} devices aggressively kill background apps. Follow the steps below to keep NovaTerm running."
                            else
                                "Battery optimization is disabled. NovaTerm will run reliably in the background."
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (needsWhitelist) Icons.Default.Warning
                            else Icons.Default.CheckCircle,
                            contentDescription = if (needsWhitelist) "Warning" else "OK",
                        )
                    }
                )
            }

            if (needsWhitelist) {
                Spacer(modifier = Modifier.height(16.dp))

                // Primary action: system battery optimization dialog.
                Button(
                    onClick = onRequestBatteryExemption,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disable battery optimization")
                }

                // Secondary action: OEM-specific autostart settings (if available).
                if (onOpenAutostartSettings != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenAutostartSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open autostart settings")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Steps for ${oemInfo.brand.displayName}",
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

                // Device info footer.
                Text(
                    text = "Device: ${oemInfo.manufacturer} ${oemInfo.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Android ${oemInfo.androidVersionName} (API ${oemInfo.androidVersion})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
