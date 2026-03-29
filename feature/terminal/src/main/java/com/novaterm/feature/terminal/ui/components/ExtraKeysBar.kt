package com.novaterm.feature.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ExtraKey(
    val label: String,
    val code: String = label,
    val popup: String? = null,
    val isModifier: Boolean = false,
)

// Escape sequences for popup keys — centralized to avoid magic strings.
private object Sequences {
    const val PAGE_UP = "\u001b[5~"
    const val PAGE_DOWN = "\u001b[6~"
    const val HOME = "\u001b[H"
    const val END = "\u001b[F"
    const val CTRL_D = "\u0004"
    const val CTRL_C = "\u0003"
    const val CTRL_R = "\u0012"
    const val CTRL_L = "\u000C"
    const val CTRL_Z = "\u001a"
}

// Layout optimized for vibe coders using AI agents (Claude Code, Gemini CLI, aider).
// Tap = primary action, Long press = popup secondary action.

// Layout organized by function zones:
// Row 1: [modifiers] [up] [action] [symbols]
// Row 2: [ESC] [left] [down] [right] [enter] [symbols]
// Arrows form a natural cross pattern across both rows.

private val ROW_1 = listOf(
    ExtraKey("CTRL", isModifier = true, popup = "^C"),  // Modifier
    ExtraKey("ALT", isModifier = true, popup = "^R"),   // Modifier
    ExtraKey("▲", "\u001b[A", popup = "PgUp"),          // Up (center of cross)
    ExtraKey("TAB", "\t", popup = "@"),                  // Tab / @
    ExtraKey("/", popup = "\\"),                         // Slash / backslash
    ExtraKey("|", popup = "~"),                          // Pipe / home
    ExtraKey("-", popup = "_"),                          // Dash / underscore
)

private val ROW_2 = listOf(
    ExtraKey("ESC", "\u001b", popup = "exit"),          // Escape
    ExtraKey("◀", "\u001b[D", popup = "Home"),          // Left
    ExtraKey("▼", "\u001b[B", popup = "PgDn"),          // Down (completes cross)
    ExtraKey("▶", "\u001b[C", popup = "End"),           // Right
    ExtraKey("⏎", "\r", popup = "^Z"),                  // Enter
)

private val ROWS = listOf(ROW_1, ROW_2)

/**
 * Two-row bar of shortcut keys rendered above the soft keyboard.
 *
 * @param onKey          Sends a raw key code string to the terminal session.
 * @param onCtrlToggle   Toggles the CTRL modifier.
 * @param onAltToggle    Toggles the ALT modifier.
 * @param ctrlActive     Whether CTRL is currently toggled on.
 * @param altActive      Whether ALT is currently toggled on.
 * @param hapticEnabled  Respect the user's haptic preference.
 */
@Composable
fun ExtraKeysBar(
    onKey: (String) -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    ctrlActive: Boolean = false,
    altActive: Boolean = false,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    // Use MaterialTheme surface colors with Gruvbox-compatible fallbacks.
    val barBackground = MaterialTheme.colorScheme.surfaceContainerLowest

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBackground)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ROWS.forEach { row ->
            ExtraKeyRow(row, onKey, onCtrlToggle, onAltToggle, ctrlActive, altActive, hapticEnabled, haptic)
        }
    }
}

@Composable
private fun ExtraKeyRow(
    keys: List<ExtraKey>,
    onKey: (String) -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    ctrlActive: Boolean,
    altActive: Boolean,
    hapticEnabled: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    // Capture latest callbacks so pointerInput closures never go stale.
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnCtrlToggle by rememberUpdatedState(onCtrlToggle)
    val currentOnAltToggle by rememberUpdatedState(onAltToggle)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { key ->
            val isActive = when (key.label) {
                "CTRL" -> ctrlActive
                "ALT" -> altActive
                else -> false
            }

            ExtraKeyButton(
                key = key,
                isActive = isActive,
                hapticEnabled = hapticEnabled,
                onClick = {
                    if (hapticEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    when (key.label) {
                        "CTRL" -> currentOnCtrlToggle()
                        "ALT" -> currentOnAltToggle()
                        else -> currentOnKey(key.code)
                    }
                },
                onLongClick = {
                    if (key.popup != null) {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        when (key.popup) {
                            "PgUp" -> currentOnKey(Sequences.PAGE_UP)
                            "PgDn" -> currentOnKey(Sequences.PAGE_DOWN)
                            "Home" -> currentOnKey(Sequences.HOME)
                            "End" -> currentOnKey(Sequences.END)
                            "exit" -> currentOnKey(Sequences.CTRL_D)
                            "^C" -> currentOnKey(Sequences.CTRL_C)
                            "^R" -> currentOnKey(Sequences.CTRL_R)
                            "^Z" -> currentOnKey(Sequences.CTRL_Z)
                            else -> currentOnKey(key.popup)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ExtraKeyButton(
    key: ExtraKey,
    isActive: Boolean,
    hapticEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    // Use rememberUpdatedState to avoid stale captures in pointerInput.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    // Theme-aware colors with Gruvbox fallbacks.
    val accentColor = MaterialTheme.colorScheme.primary          // Ember Orange for active modifiers
    val accentOnColor = MaterialTheme.colorScheme.onPrimary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceContainerHigh
    val pressedColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor = MaterialTheme.colorScheme.onSurface

    val bgColor = when {
        isActive -> accentColor
        pressed -> pressedColor
        key.isModifier -> surfaceVariant
        else -> surfaceColor
    }

    val fgColor = when {
        isActive -> accentOnColor
        else -> textColor
    }

    // Build accessibility description.
    val description = buildString {
        append(key.label)
        if (key.isModifier) append(" modifier key")
        if (key.popup != null) append(", long press for ${key.popup}")
    }

    val stateDesc = when {
        key.isModifier && isActive -> "Active"
        key.isModifier -> "Inactive"
        else -> null
    }

    Box(
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = if (key.isModifier) Role.Switch else Role.Button
                if (stateDesc != null) {
                    stateDescription = stateDesc
                }
            }
            .pointerInput(key.label) { // key.label is stable, avoids stale closure reuse
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick() }
                )
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = key.label,
                color = fgColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (key.popup != null) {
                Text(
                    text = key.popup,
                    color = fgColor.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
