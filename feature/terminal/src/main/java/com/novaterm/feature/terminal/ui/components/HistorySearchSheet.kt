package com.novaterm.feature.terminal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.novaterm.feature.terminal.R

/**
 * A command from the block store history.
 */
data class HistoryEntry(
    val command: String,
    val cwd: String?,
    val exitCode: Int?,
    val timestamp: Long,
    val isAiGenerated: Boolean,
)

/**
 * Bottom sheet with fuzzy search over command history from the BlockStore.
 * Allows users to find and re-execute previous commands.
 *
 * Features:
 * - Real-time fuzzy filtering as user types
 * - Shows exit code (green check / red X)
 * - Shows working directory context
 * - Shows timestamp
 * - AI-generated commands marked with indicator
 * - Tap to insert command into terminal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySearchSheet(
    entries: List<HistoryEntry>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, entries) {
        if (query.isBlank()) {
            entries.takeLast(100).reversed()
        } else {
            val q = query.lowercase()
            entries.filter { it.command.lowercase().contains(q) }
                .takeLast(100)
                .reversed()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_commands_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_commands_hint)) },
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.search_results_count, filtered.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            ) {
                items(filtered) { entry ->
                    HistoryRow(
                        entry = entry,
                        onClick = {
                            onSelect(entry.command)
                            onDismiss()
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val exitIcon = when (entry.exitCode) {
        0 -> "✓"
        null -> "·"
        else -> "✗"
    }
    val exitColor = when (entry.exitCode) {
        0 -> MaterialTheme.colorScheme.tertiary
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Exit code indicator
        Text(
            text = exitIcon,
            color = exitColor,
            fontSize = 14.sp,
            modifier = Modifier.width(20.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.isAiGenerated) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "AI",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (entry.cwd != null) {
                Text(
                    text = entry.cwd.replace(Regex(".*/home/"), "~/"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Timestamp
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
