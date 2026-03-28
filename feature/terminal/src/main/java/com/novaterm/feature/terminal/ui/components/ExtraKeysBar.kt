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

// Layout optimized for vibe coders using AI agents (Claude Code, Gemini CLI, aider)
// Focus: natural language prompts, slash commands, file references, navigation
// Each key has a popup (long press) for secondary action

// Layout optimized for vibe coders using AI agents (Claude Code, Gemini CLI, aider)
// Tap = primary action, Long press = popup secondary action

private val ROW_1 = listOf(
    ExtraKey("ESC", "\u001b", popup = "exit"),       // Rewind in Claude Code, popup: Ctrl+D exit
    ExtraKey("TAB", "\t", popup = "@"),               // Autocomplete @file paths, popup: @
    ExtraKey("UP", "\u001b[A", popup = "PgUp"),       // Prompt history, popup: page up
    ExtraKey("/", popup = "~"),                        // Slash commands, popup: home dir ~
    ExtraKey("DOWN", "\u001b[B", popup = "PgDn"),     // Prompt history, popup: page down
    ExtraKey("ENT", "\r"),                             // Send prompt / execute command
)

private val ROW_2 = listOf(
    ExtraKey("CTRL", isModifier = true, popup = "^C"),  // Modifier, popup: Ctrl+C cancel
    ExtraKey("ALT", isModifier = true, popup = "^R"),   // Modifier, popup: Ctrl+R search history
    ExtraKey("LEFT", "\u001b[D", popup = "Home"),       // Cursor left, popup: start of line
    ExtraKey("RIGHT", "\u001b[C", popup = "End"),       // Cursor right, popup: end of line
    ExtraKey("CLR", "\u000C", popup = "|"),              // Clear screen (Ctrl+L), popup: pipe
)

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
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ExtraKeyRow(ROW_1, onKey, onCtrlToggle, onAltToggle, ctrlActive, altActive, hapticEnabled, haptic)
        ExtraKeyRow(ROW_2, onKey, onCtrlToggle, onAltToggle, ctrlActive, altActive, hapticEnabled, haptic)
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
        horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                            "PgUp" -> currentOnKey("\u001b[5~")
                            "PgDn" -> currentOnKey("\u001b[6~")
                            "Home" -> currentOnKey("\u001b[H")
                            "End" -> currentOnKey("\u001b[F")
                            "@" -> currentOnKey("@")
                            "~" -> currentOnKey("~")
                            "exit" -> currentOnKey("\u0004")  // Ctrl+D
                            "^C" -> currentOnKey("\u0003")    // Ctrl+C (cancel AI generation)
                            "^R" -> currentOnKey("\u0012")    // Ctrl+R (search history)
                            "clr" -> currentOnKey("\u000C")   // Ctrl+L (clear screen)
                            "|" -> currentOnKey("|")          // Pipe
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
    val accentColor = MaterialTheme.colorScheme.tertiary        // Gruvbox orange in our theme
    val accentOnColor = MaterialTheme.colorScheme.onTertiary
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
            .height(42.dp)
            .widthIn(min = 42.dp)
            .clip(RoundedCornerShape(4.dp))
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
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (key.popup != null) {
                Text(
                    text = key.popup,
                    color = fgColor.copy(alpha = 0.4f),
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
