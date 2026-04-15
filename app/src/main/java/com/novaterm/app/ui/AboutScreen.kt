package com.novaterm.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novaterm.app.R
import com.novaterm.app.crash.CrashReporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var crashLogCount by remember { mutableStateOf(CrashReporter.getCrashLogCount(context)) }
    var showCrashDialog by remember { mutableStateOf(false) }
    var crashLogContent by remember { mutableStateOf<String?>(null) }

    crashLogContent?.let { content ->
        if (showCrashDialog) {
            CrashLogDialog(
                content = content,
                onShare = { shareCrashLog(context, content) },
                onDismiss = { showCrashDialog = false },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                text = "v${com.novaterm.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Device info
            SectionHeader(stringResource(R.string.about_section_device))
            InfoRow(stringResource(R.string.about_label_model), Build.MODEL)
            InfoRow(
                stringResource(R.string.about_label_android),
                stringResource(R.string.about_android_value, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            )
            InfoRow(
                stringResource(R.string.about_label_architecture),
                Build.SUPPORTED_ABIS.firstOrNull() ?: stringResource(R.string.about_unknown),
            )
            InfoRow(stringResource(R.string.about_label_manufacturer), Build.MANUFACTURER)

            Spacer(modifier = Modifier.height(16.dp))

            // Terminal info
            SectionHeader(stringResource(R.string.about_section_terminal))
            InfoRow(stringResource(R.string.about_label_term), stringResource(R.string.about_value_term))
            InfoRow(stringResource(R.string.about_label_color), stringResource(R.string.about_value_color))
            InfoRow(stringResource(R.string.about_label_unicode), stringResource(R.string.about_value_unicode))
            InfoRow(stringResource(R.string.about_label_scrollback), stringResource(R.string.about_value_scrollback))

            Spacer(modifier = Modifier.height(16.dp))

            // Architecture
            SectionHeader(stringResource(R.string.about_section_architecture))
            InfoRow(stringResource(R.string.about_label_ui), stringResource(R.string.about_value_ui))
            InfoRow(stringResource(R.string.about_label_core), stringResource(R.string.about_value_core))
            InfoRow(stringResource(R.string.about_label_persistence), stringResource(R.string.about_value_persistence))
            InfoRow(stringResource(R.string.about_label_protocols), stringResource(R.string.about_value_protocols))

            Spacer(modifier = Modifier.height(16.dp))

            // Credits
            SectionHeader(stringResource(R.string.about_section_credits))
            Text(
                text = stringResource(R.string.about_credits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Crash logs
            SectionHeader(stringResource(R.string.about_section_crash_logs))
            if (crashLogCount == 0) {
                Text(
                    text = stringResource(R.string.about_crash_logs_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.about_crash_logs_count, crashLogCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            crashLogContent = CrashReporter.getLatestCrashLog(context)
                            showCrashDialog = true
                        },
                    ) {
                        Text(stringResource(R.string.about_crash_logs_view))
                    }
                    OutlinedButton(
                        onClick = {
                            CrashReporter.clearCrashLogs(context)
                            crashLogCount = 0
                        },
                    ) {
                        Text(stringResource(R.string.about_crash_logs_clear))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CrashLogDialog(
    content: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_crash_dialog_title)) },
        text = {
            Surface(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onShare) {
                Text(stringResource(R.string.about_crash_dialog_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_crash_dialog_close))
            }
        },
    )
}

private fun shareCrashLog(context: Context, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "NovaTerm crash report")
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.about_crash_share_chooser))
    )
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
