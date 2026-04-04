package com.novaterm.feature.terminal.ui.components

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaterm.feature.terminal.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ExtraKey(
    val label: String,
    val code: String = label,
    val popup: String? = null,
    val isModifier: Boolean = false,
    val tooltipKey: String? = null,  // Key for tooltip tracking (CTRL, ALT, TAB, ESC)
)

/**
 * Manages first-use tooltip display counts via SharedPreferences.
 * Tooltips are shown for the first [MAX_TOOLTIP_SHOWS] interactions.
 */
object TooltipPreferences {
    private const val PREFS_NAME = "extra_keys_tooltips"
    private const val MAX_TOOLTIP_SHOWS = 3

    private const val KEY_CTRL_COUNT = "tooltip_ctrl_count"
    private const val KEY_ALT_COUNT = "tooltip_alt_count"
    private const val KEY_TAB_COUNT = "tooltip_tab_count"
    private const val KEY_ESC_COUNT = "tooltip_esc_count"
    private const val KEY_LONG_PRESS_COUNT = "tooltip_long_press_count"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getCountKey(tooltipKey: String): String = when (tooltipKey) {
        "CTRL" -> KEY_CTRL_COUNT
        "ALT" -> KEY_ALT_COUNT
        "TAB" -> KEY_TAB_COUNT
        "ESC" -> KEY_ESC_COUNT
        "LONG_PRESS" -> KEY_LONG_PRESS_COUNT
        else -> "tooltip_${tooltipKey.lowercase()}_count"
    }

    /**
     * Returns true if the tooltip should be shown (count < MAX_TOOLTIP_SHOWS).
     */
    fun shouldShowTooltip(context: Context, tooltipKey: String): Boolean {
        val prefs = getPrefs(context)
        val count = prefs.getInt(getCountKey(tooltipKey), 0)
        return count < MAX_TOOLTIP_SHOWS
    }

    /**
     * Increments the tooltip display count for the given key.
     */
    fun incrementTooltipCount(context: Context, tooltipKey: String) {
        val prefs = getPrefs(context)
        val key = getCountKey(tooltipKey)
        val count = prefs.getInt(key, 0)
        prefs.edit().putInt(key, count + 1).apply()
    }
}

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
    ExtraKey("CTRL", isModifier = true, popup = "^C", tooltipKey = "CTRL"),  // Modifier
    ExtraKey("ALT", isModifier = true, popup = "^R", tooltipKey = "ALT"),    // Modifier
    ExtraKey("▲", "\u001b[A", popup = "PgUp"),          // Up (center of cross)
    ExtraKey("TAB", "\t", popup = "@", tooltipKey = "TAB"),                   // Tab / @
    ExtraKey("/", popup = "\\"),                         // Slash / backslash
    ExtraKey("|", popup = "~"),                          // Pipe / home
    ExtraKey("-", popup = "_"),                          // Dash / underscore
)

private val ROW_2 = listOf(
    ExtraKey("ESC", "\u001b", popup = "exit", tooltipKey = "ESC"),           // Escape
    ExtraKey("◀", "\u001b[D", popup = "Home"),          // Left
    ExtraKey("▼", "\u001b[B", popup = "PgDn"),          // Down (completes cross)
    ExtraKey("▶", "\u001b[C", popup = "End"),           // Right
    ExtraKey("⏎", "\r", popup = "^Z"),                  // Enter
    ExtraKey("⌨", code = "__KEYBOARD__"),               // Keyboard toggle
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
    onKeyboardToggle: () -> Unit = {},
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
            ExtraKeyRow(row, onKey, onCtrlToggle, onAltToggle, onKeyboardToggle, ctrlActive, altActive, hapticEnabled, haptic)
        }
    }
}

@Composable
private fun ExtraKeyRow(
    keys: List<ExtraKey>,
    onKey: (String) -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onKeyboardToggle: () -> Unit,
    ctrlActive: Boolean,
    altActive: Boolean,
    hapticEnabled: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    // Capture latest callbacks so pointerInput closures never go stale.
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnCtrlToggle by rememberUpdatedState(onCtrlToggle)
    val currentOnAltToggle by rememberUpdatedState(onAltToggle)
    val currentOnKeyboardToggle by rememberUpdatedState(onKeyboardToggle)

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
                    when {
                        key.label == "CTRL" -> currentOnCtrlToggle()
                        key.label == "ALT" -> currentOnAltToggle()
                        key.code == "__KEYBOARD__" -> currentOnKeyboardToggle()
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

/**
 * Returns the tooltip text for a given key, or null if no tooltip is defined.
 */
@Composable
private fun getTooltipText(tooltipKey: String?): String? = when (tooltipKey) {
    "CTRL" -> stringResource(R.string.tooltip_ctrl)
    "ALT" -> stringResource(R.string.tooltip_alt)
    "TAB" -> stringResource(R.string.tooltip_tab)
    "ESC" -> stringResource(R.string.tooltip_esc)
    "LONG_PRESS" -> stringResource(R.string.tooltip_long_press)
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraKeyButton(
    key: ExtraKey,
    isActive: Boolean,
    hapticEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    // Tooltip state management
    val tooltipState = rememberTooltipState()
    val tooltipText = getTooltipText(key.tooltipKey)
    val longPressTooltipText = getTooltipText("LONG_PRESS")

    // Track if we should show tooltip on this interaction
    val shouldShowKeyTooltip = remember(key.tooltipKey) {
        key.tooltipKey != null && TooltipPreferences.shouldShowTooltip(context, key.tooltipKey)
    }
    val shouldShowLongPressTooltip = remember(key.popup) {
        key.popup != null && TooltipPreferences.shouldShowTooltip(context, "LONG_PRESS")
    }

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

    // Determine which tooltip to show (key-specific takes priority)
    val activeTooltipText = when {
        shouldShowKeyTooltip && tooltipText != null -> tooltipText
        shouldShowLongPressTooltip && longPressTooltipText != null -> longPressTooltipText
        else -> null
    }

    // Wrapper with optional tooltip
    val buttonContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 48.dp)
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
                        onTap = {
                            // Show tooltip on tap if applicable
                            if (shouldShowKeyTooltip && tooltipText != null) {
                                scope.launch {
                                    tooltipState.show()
                                    TooltipPreferences.incrementTooltipCount(context, key.tooltipKey!!)
                                    delay(2500L) // Show for 2.5 seconds
                                    tooltipState.dismiss()
                                }
                            }
                            currentOnClick()
                        },
                        onLongPress = {
                            // Show long-press tooltip hint if applicable (and no key-specific tooltip)
                            if (!shouldShowKeyTooltip && shouldShowLongPressTooltip && key.popup != null) {
                                scope.launch {
                                    tooltipState.show()
                                    TooltipPreferences.incrementTooltipCount(context, "LONG_PRESS")
                                    delay(2500L)
                                    tooltipState.dismiss()
                                }
                            }
                            currentOnLongClick()
                        }
                    )
                }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = key.label,
                color = fgColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }

    // Render with or without TooltipBox based on whether we have tooltip text
    if (activeTooltipText != null) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(activeTooltipText)
                }
            },
            state = tooltipState,
        ) {
            buttonContent()
        }
    } else {
        buttonContent()
    }
}
