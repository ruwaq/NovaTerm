package com.novaterm.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaterm.core.bootstrap.BootstrapInstaller
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Terminal-style boot sequence screen shown during first-launch bootstrap.
 * Simulates a system boot with real progress interleaved with feature tips.
 * The entire screen uses monospace font on the terminal background color
 * to feel like part of the terminal experience, not a generic loading screen.
 */
@Composable
fun BootstrapScreen(
    installer: BootstrapInstaller,
    onComplete: () -> Unit,
) {
    val state by installer.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Boot log lines that appear sequentially
    val bootLines = remember { mutableStateListOf<BootLine>() }
    val tipIndex = remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Start with branding
        bootLines.add(BootLine("NovaTerm v0.1.0", LineType.HEADER))
        delay(200)
        bootLines.add(BootLine("Next-gen Android terminal", LineType.DIM))
        delay(300)
        bootLines.add(BootLine("", LineType.BLANK))

        // Boot sequence
        bootLines.add(BootLine("[ok] Terminal core loaded", LineType.OK))
        delay(200)
        bootLines.add(BootLine("[ok] Gruvbox Ember theme applied", LineType.OK))
        delay(150)
        bootLines.add(BootLine("[ok] 7 color schemes available", LineType.OK))
        delay(200)

        // First tip
        bootLines.add(BootLine("", LineType.BLANK))
        bootLines.add(BootLine("  tip: swipe tabs to switch sessions", LineType.TIP))
        delay(400)
        bootLines.add(BootLine("", LineType.BLANK))

        // Start the actual install
        val success = installer.installIfNeeded()

        if (success) {
            bootLines.add(BootLine("[ok] Packages extracted", LineType.OK))
            delay(150)
            bootLines.add(BootLine("[ok] bash, apt, coreutils ready", LineType.OK))
            delay(200)

            // Second tip
            bootLines.add(BootLine("", LineType.BLANK))
            bootLines.add(BootLine("  tip: long-press extra keys for popups", LineType.TIP))
            delay(300)
            bootLines.add(BootLine("", LineType.BLANK))

            bootLines.add(BootLine("[ok] Shell environment configured", LineType.OK))
            delay(150)
            bootLines.add(BootLine("[ok] Session persistence active", LineType.OK))
            delay(200)

            // Features showcase
            bootLines.add(BootLine("", LineType.BLANK))
            bootLines.add(BootLine("  ✓ Clickable URLs in output", LineType.FEATURE))
            bootLines.add(BootLine("  ✓ Smart notifications", LineType.FEATURE))
            bootLines.add(BootLine("  ✓ Command history search", LineType.FEATURE))
            bootLines.add(BootLine("  ✓ Crash-safe session restore", LineType.FEATURE))
            delay(400)

            bootLines.add(BootLine("", LineType.BLANK))
            bootLines.add(BootLine("[ok] Ready.", LineType.ACCENT))
            delay(500)

            onComplete()
        }
    }

    // Update boot lines from installer state
    LaunchedEffect(state) {
        when (val s = state) {
            is BootstrapInstaller.State.Extracting -> {
                val pct = (s.progress * 100).toInt()
                // Update or add extraction progress line
                val existingIdx = bootLines.indexOfLast { it.type == LineType.PROGRESS }
                val line = BootLine("[>>] Extracting packages... $pct%", LineType.PROGRESS)
                if (existingIdx >= 0) {
                    bootLines[existingIdx] = line
                } else {
                    bootLines.add(line)
                }
            }
            is BootstrapInstaller.State.Finalizing -> {
                bootLines.add(BootLine("[>>] Finalizing...", LineType.PROGRESS))
            }
            is BootstrapInstaller.State.Error -> {
                bootLines.add(BootLine("", LineType.BLANK))
                bootLines.add(BootLine("[!!] ${s.message}", LineType.ERROR))
            }
            else -> {}
        }
    }

    // Terminal-style full screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        bootLines.forEach { line ->
            AnimatedVisibility(visible = true, enter = fadeIn()) {
                BootLineText(line)
            }
        }

        // Error retry button
        if (state is BootstrapInstaller.State.Error) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                bootLines.clear()
                scope.launch {
                    if (installer.install()) onComplete()
                }
            }) {
                Text("Retry")
            }
        }
    }
}

// ── Boot line types ──────────────────────────────────

private enum class LineType {
    HEADER, DIM, BLANK, OK, PROGRESS, TIP, FEATURE, ACCENT, ERROR,
}

private data class BootLine(val text: String, val type: LineType)

@Composable
private fun BootLineText(line: BootLine) {
    if (line.type == LineType.BLANK) {
        Spacer(Modifier.height(6.dp))
        return
    }

    val color = when (line.type) {
        LineType.HEADER -> MaterialTheme.colorScheme.primary
        LineType.DIM -> MaterialTheme.colorScheme.onSurfaceVariant
        LineType.OK -> MaterialTheme.colorScheme.tertiary
        LineType.PROGRESS -> MaterialTheme.colorScheme.primary
        LineType.TIP -> MaterialTheme.colorScheme.onSurfaceVariant
        LineType.FEATURE -> MaterialTheme.colorScheme.secondary
        LineType.ACCENT -> MaterialTheme.colorScheme.primary
        LineType.ERROR -> MaterialTheme.colorScheme.error
        LineType.BLANK -> MaterialTheme.colorScheme.onSurface
    }

    val weight = when (line.type) {
        LineType.HEADER -> FontWeight.Bold
        LineType.ACCENT -> FontWeight.Bold
        else -> FontWeight.Normal
    }

    val size = when (line.type) {
        LineType.HEADER -> 20.sp
        LineType.TIP -> 12.sp
        LineType.FEATURE -> 13.sp
        else -> 14.sp
    }

    Text(
        text = line.text,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        fontWeight = weight,
        color = color,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
