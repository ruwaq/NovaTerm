package com.novaterm.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // App name and version
            Text(
                text = "NovaTerm",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "v0.1.0",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Next-gen Android terminal emulator",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Device info
            SectionHeader("Device")
            InfoRow("Model", Build.MODEL)
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow("Architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            InfoRow("Manufacturer", Build.MANUFACTURER)

            Spacer(modifier = Modifier.height(16.dp))

            // Terminal info
            SectionHeader("Terminal")
            InfoRow("TERM", "xterm-256color")
            InfoRow("Color", "Truecolor (24-bit)")
            InfoRow("Unicode", "Full support")
            InfoRow("Scrollback", "10,000 lines")

            Spacer(modifier = Modifier.height(16.dp))

            // Architecture
            SectionHeader("Architecture")
            InfoRow("UI", "Kotlin + Jetpack Compose")
            InfoRow("Core", "Termux terminal-emulator (Apache 2.0)")
            InfoRow("Persistence", "SQLite + CAS dedup")
            InfoRow("Protocols", "OSC 8, OSC 133, OSC 52")

            Spacer(modifier = Modifier.height(16.dp))

            // Credits
            SectionHeader("Credits")
            Text(
                text = "Built on Termux's terminal emulation engine.\n" +
                       "Terminal core licensed under Apache 2.0.\n" +
                       "Color schemes: Gruvbox, Catppuccin, Solarized, Monokai, Nord, Dracula.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
