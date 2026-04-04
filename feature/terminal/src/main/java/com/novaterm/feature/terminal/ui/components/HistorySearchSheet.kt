package com.novaterm.feature.terminal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaterm.feature.terminal.R
import com.termux.terminal.TerminalBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 * Filter options for history entries.
 */
enum class HistoryFilter {
    ALL,
    SUCCESS,
    FAILED,
}

/**
 * Bottom sheet with two tabs:
 * 1. **Commands** — fuzzy search over command history from the BlockStore
 * 2. **Output** — regex-capable search over terminal scrollback buffer text
 *
 * The Commands tab allows users to find and re-execute previous commands.
 * The Output tab searches the actual terminal content (scrollback + visible screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySearchSheet(
    entries: List<HistoryEntry>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Supply a TerminalBuffer to enable the Output search tab. Null disables it. */
    terminalBuffer: TerminalBuffer? = null,
    /** Called when user navigates to a match row (external row coordinate). */
    onScrollToRow: ((Int) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Tab row — only show if buffer search is available
            if (terminalBuffer != null) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.tab_commands)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.tab_output)) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            when (selectedTab) {
                0 -> CommandSearchTab(entries, onSelect, onDismiss)
                1 -> if (terminalBuffer != null) {
                    OutputSearchTab(terminalBuffer, onScrollToRow)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Commands tab (original behavior) ─────────────────────────────────

@Composable
private fun CommandSearchTab(
    entries: List<HistoryEntry>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var newestFirst by remember { mutableStateOf(true) }

    val filtered = remember(query, entries, filter, newestFirst) {
        var result = entries

        // Apply filter
        result = when (filter) {
            HistoryFilter.ALL -> result
            HistoryFilter.SUCCESS -> result.filter { it.exitCode == 0 }
            HistoryFilter.FAILED -> result.filter { it.exitCode != null && it.exitCode != 0 }
        }

        // Apply search query
        if (query.isNotBlank()) {
            val q = query.lowercase()
            result = result.filter { it.command.lowercase().contains(q) }
        }

        // Apply sort and limit
        result = if (newestFirst) {
            result.takeLast(500).reversed()
        } else {
            result.take(500)
        }

        result
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_commands_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_commands_hint)) },
        singleLine = true,
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Filter chips and sort toggle row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter == HistoryFilter.ALL,
            onClick = { filter = HistoryFilter.ALL },
            label = { Text(stringResource(R.string.filter_all)) },
        )
        FilterChip(
            selected = filter == HistoryFilter.SUCCESS,
            onClick = { filter = HistoryFilter.SUCCESS },
            label = { Text(stringResource(R.string.filter_success)) },
        )
        FilterChip(
            selected = filter == HistoryFilter.FAILED,
            onClick = { filter = HistoryFilter.FAILED },
            label = { Text(stringResource(R.string.filter_failed)) },
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = { newestFirst = !newestFirst }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = if (newestFirst) {
                    stringResource(R.string.sort_oldest_first)
                } else {
                    stringResource(R.string.sort_newest_first)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

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
}

// ── Output search tab ────────────────────────────────────────────────

@Composable
private fun OutputSearchTab(
    buffer: TerminalBuffer,
    onScrollToRow: ((Int) -> Unit)?,
) {
    var query by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<TerminalBuffer.SearchMatch>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var regexError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Run search when query or options change
    LaunchedEffect(query, useRegex, caseSensitive) {
        if (query.isBlank()) {
            matches = emptyList()
            currentIndex = 0
            regexError = false
            return@LaunchedEffect
        }
        // Run search off main thread since scrollback can be large
        withContext(Dispatchers.Default) {
            val results = buffer.searchText(query, useRegex, caseSensitive)
            withContext(Dispatchers.Main) {
                matches = results
                regexError = useRegex && results.isEmpty() && query.isNotBlank()
                currentIndex = if (results.isNotEmpty()) results.size - 1 else 0
            }
        }
    }

    // Navigate to current match when index changes
    LaunchedEffect(currentIndex, matches) {
        if (matches.isNotEmpty() && currentIndex in matches.indices) {
            onScrollToRow?.invoke(matches[currentIndex].row)
            // Scroll the results list to show the current match
            listState.animateScrollToItem(currentIndex)
        }
    }

    // Search field with regex/case toggles
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_output_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        isError = regexError,
        supportingText = if (regexError) {
            { Text("Invalid regex pattern") }
        } else {
            null
        },
        singleLine = true,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Regex toggle
                FilterChip(
                    selected = useRegex,
                    onClick = { useRegex = !useRegex },
                    label = { Text(stringResource(R.string.search_regex), fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                // Case-sensitive toggle
                FilterChip(
                    selected = caseSensitive,
                    onClick = { caseSensitive = !caseSensitive },
                    label = { Text(stringResource(R.string.search_case_sensitive), fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        },
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Navigation row: match count + prev/next buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (matches.isEmpty()) {
                if (query.isBlank()) "" else stringResource(R.string.search_no_matches)
            } else {
                stringResource(R.string.search_output_count, currentIndex + 1, matches.size)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row {
            IconButton(
                onClick = {
                    if (matches.isNotEmpty()) {
                        currentIndex = if (currentIndex > 0) currentIndex - 1 else matches.size - 1
                    }
                },
                enabled = matches.isNotEmpty(),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.search_previous),
                )
            }
            IconButton(
                onClick = {
                    if (matches.isNotEmpty()) {
                        currentIndex = if (currentIndex < matches.size - 1) currentIndex + 1 else 0
                    }
                },
                enabled = matches.isNotEmpty(),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.search_next),
                )
            }
        }
    }

    // Results list showing matched lines
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
    ) {
        items(matches.size) { index ->
            val match = matches[index]
            val isSelected = index == currentIndex
            OutputMatchRow(
                match = match,
                isSelected = isSelected,
                onClick = {
                    currentIndex = index
                    onScrollToRow?.invoke(match.row)
                },
            )
        }
    }
}

// ── Row composables ──────────────────────────────────────────────────

@Composable
private fun OutputMatchRow(
    match: TerminalBuffer.SearchMatch,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Row number indicator
        Text(
            text = "${match.row}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
        // Line text, trimmed
        Text(
            text = match.lineText.trim(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
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
