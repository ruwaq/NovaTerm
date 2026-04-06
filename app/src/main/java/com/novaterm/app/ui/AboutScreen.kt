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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novaterm.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
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
