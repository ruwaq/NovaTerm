package com.novaterm.feature.agent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaterm.feature.agent.data.DiffParser.DiffFile
import com.novaterm.feature.agent.data.DiffParser.DiffHunk
import com.novaterm.feature.agent.data.DiffParser.DiffLine
import com.novaterm.feature.agent.data.DiffParser.DiffLineType
import com.novaterm.feature.agent.data.DiffParser.DiffResult
import com.novaterm.feature.agent.data.DiffParser.DiffStats

// ── Color palette for diff rendering ────────────────────────────

/** Background colors for diff lines, matching Gruvbox terminal palette. */
private val DiffLineType.bgColor: Color
    get() = when (this) {
        DiffLineType.ADD -> Color(0xFF1D3B1D)       // Dark green
        DiffLineType.REMOVE -> Color(0xFF3B1D1D)    // Dark red
        DiffLineType.HUNK_HEADER -> Color(0xFF1A3344) // Dark cyan/teal
        DiffLineType.FILE_HEADER -> Color(0xFF2D2640) // Dark purple
        else -> Color.Transparent
    }

/** Text colors for diff lines. */
private val DiffLineType.textColor: Color
    get() = when (this) {
        DiffLineType.ADD -> Color(0xFFA9D89D)       // Soft green
        DiffLineType.REMOVE -> Color(0xFFD99A9A)    // Soft red
        DiffLineType.HUNK_HEADER -> Color(0xFF7DC4CF) // Cyan
        DiffLineType.FILE_HEADER -> Color(0xFFC4A7E7) // Purple
        DiffLineType.META -> Color(0xFF9CA3AF)       // Gray
        DiffLineType.NO_NEWLINE -> Color(0xFFE5C07B)  // Yellow
        DiffLineType.CONTEXT -> Color(0xFFC0C0C0)     // Light gray
    }

/** Prefix character for each line type (monospace gutter). */
private val DiffLineType.prefix: Char
    get() = when (this) {
        DiffLineType.ADD -> '+'
        DiffLineType.REMOVE -> '-'
        DiffLineType.CONTEXT -> ' '
        DiffLineType.HUNK_HEADER -> '@'
        DiffLineType.FILE_HEADER -> 'd'
        DiffLineType.META -> ' '
        DiffLineType.NO_NEWLINE -> '\\'
    }

// ── Main screen ──────────────────────────────────────────────────

/**
 * Full-screen diff viewer for reviewing agent workspace changes.
 *
 * Displays [DiffResult] with color-coded lines and action bar:
 * - **Approve**: commit all changes (`git add -A && git commit`)
 * - **Reject**: discard all changes (`git checkout .`)
 * - **Open in Terminal**: switch to the workspace's terminal session
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerScreen(
    diffResult: DiffResult,
    workspaceName: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenTerminal: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showApproveDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Diff: $workspaceName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = diffResult.stats.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = { showApproveDialog = true },
                        enabled = diffResult.files.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Approve")
                    }

                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        enabled = diffResult.files.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reject")
                    }

                    OutlinedButton(onClick = onOpenTerminal) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terminal")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (diffResult.files.isEmpty()) {
            // Empty diff — no changes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "No changes detected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "This workspace has no uncommitted changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // File stats summary bar
                item {
                    FileStatsBar(stats = diffResult.stats)
                }

                // Each file section
                diffResult.files.forEachIndexed { fileIndex, file ->
                    // File header
                    item(key = "file-$fileIndex") {
                        FileHeader(file = file)
                    }

                    // Hunks with lines
                    file.hunks.forEachIndexed { hunkIndex, hunk ->
                        item(key = "file-$fileIndex-hunk-$hunkIndex") {
                            HunkSection(hunk = hunk)
                        }
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Approve confirmation
    if (showApproveDialog) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false },
            title = { Text("Approve changes?") },
            text = {
                Text("This will commit all changes in \"$workspaceName\" with a generated message. The agent workspace will be preserved.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onApprove()
                        showApproveDialog = false
                    },
                ) {
                    Text("Approve", color = Color(0xFF22C55E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApproveDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Reject confirmation
    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject changes?") },
            text = {
                Text("This will discard ALL uncommitted changes in \"$workspaceName\". This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReject()
                        showRejectDialog = false
                    },
                ) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Subcomponents ────────────────────────────────────────────────

/** Compact stats bar showing insertions/deletions across all files. */
@Composable
private fun FileStatsBar(stats: DiffStats, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "${stats.filesChanged} file${if (stats.filesChanged != 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stats.insertions > 0) {
            Text(
                text = "+${stats.insertions}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFA9D89D),
            )
        }
        if (stats.deletions > 0) {
            Text(
                text = "-${stats.deletions}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFD99A9A),
            )
        }
    }
}

/** File header with path, status badge, and +/- counts. */
@Composable
private fun FileHeader(file: DiffFile, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status badge
        val (badgeText, badgeColor) = when {
            file.isNew -> "NEW" to Color(0xFF22C55E)
            file.isDeleted -> "DEL" to Color(0xFFEF4444)
            file.isRenamed -> "REN" to Color(0xFF3B82F6)
            file.isBinary -> "BIN" to Color(0xFF9CA3AF)
            else -> "MOD" to Color(0xFFEAB308)
        }

        Box(
            modifier = Modifier
                .background(badgeColor.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = badgeColor,
                fontSize = 10.sp,
            )
        }

        Text(
            text = file.displayPath,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        // Add/remove counts
        if (!file.isBinary) {
            val adds = file.additions
            val dels = file.deletions
            if (adds > 0) {
                Text(
                    text = "+$adds",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA9D89D),
                )
            }
            if (dels > 0) {
                Text(
                    text = "-$dels",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD99A9A),
                )
            }
        }
    }
}

/** Renders a single hunk (header + lines) with horizontal scroll for long lines. */
@Composable
private fun HunkSection(hunk: DiffHunk, modifier: Modifier = Modifier) {
    val horizontalScroll = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        // Hunk lines
        hunk.lines.forEach { line ->
            DiffLineRow(line = line)
        }
    }
}

/** Single diff line with background color, line numbers, and content. */
@Composable
private fun DiffLineRow(line: DiffLine, modifier: Modifier = Modifier) {
    val bgColor by animateColorAsState(
        targetValue = line.type.bgColor,
        label = "diff-line-bg",
    )
    val fgColor by animateColorAsState(
        targetValue = line.type.textColor,
        label = "diff-line-fg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 0.5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Line number gutter (old + new)
        if (line.type == DiffLineType.ADD || line.type == DiffLineType.REMOVE || line.type == DiffLineType.CONTEXT) {
            Text(
                text = if (line.oldLineNumber > 0) "%4d".format(line.oldLineNumber) else "    ",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (line.newLineNumber > 0) "%4d".format(line.newLineNumber) else "    ",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Prefix character
        Text(
            text = line.type.prefix.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = fgColor,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

        // Content
        Text(
            text = line.content,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = fgColor,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            softWrap = false,
        )
    }
}