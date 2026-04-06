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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
    const val CTRL_O = "\u000F"
    const val CTRL_B = "\u0002"
}

// Layout organized by function zones:
// Row 1: [modifiers] [up] [action] [symbols]
// Row 2: [ESC] [left] [down] [right] [enter] [symbols]
// Arrows form a natural cross pattern across both rows.

// ── Default layout ─────────────────────────────────────────
// Optimized for vibe coders using AI agents (Claude Code, Gemini CLI, aider).
// Tap = primary action, Long press = popup secondary action.
private val DEFAULT_ROW_1 = listOf(
    ExtraKey("CTRL", isModifier = true, popup = "^C", tooltipKey = "CTRL"),
    ExtraKey("ALT", isModifier = true, popup = "^R", tooltipKey = "ALT"),
    ExtraKey("▲", "\u001b[A", popup = "PgUp"),
    ExtraKey("TAB", "\t", popup = "@", tooltipKey = "TAB"),
    ExtraKey("/", popup = "\\"),
    ExtraKey("|", popup = "~"),
    ExtraKey("-", popup = "_"),
)
private val DEFAULT_ROW_2 = listOf(
    ExtraKey("ESC", "\u001b", popup = "exit", tooltipKey = "ESC"),
    ExtraKey("◀", "\u001b[D", popup = "Home"),
    ExtraKey("▼", "\u001b[B", popup = "PgDn"),
    ExtraKey("▶", "\u001b[C", popup = "End"),
    ExtraKey("⏎", "\r", popup = "^Z"),
    ExtraKey("⌨", code = "__KEYBOARD__"),
)
private val DEFAULT_ROWS = listOf(DEFAULT_ROW_1, DEFAULT_ROW_2)

// ── Vim layout ─────────────────────────────────────────────
// ESC prominent, :wq shortcut, hjkl navigation hints.
private val VIM_ROW_1 = listOf(
    ExtraKey("ESC", "\u001b", popup = "exit", tooltipKey = "ESC"),
    ExtraKey("CTRL", isModifier = true, popup = "^C", tooltipKey = "CTRL"),
    ExtraKey("▲", "\u001b[A", popup = "PgUp"),
    ExtraKey(":", popup = ":wq\r"),
    ExtraKey("/", popup = "?"),
    ExtraKey("0", popup = "$"),
    ExtraKey("w", popup = "b"),
)
private val VIM_ROW_2 = listOf(
    ExtraKey("ALT", isModifier = true, popup = "^R", tooltipKey = "ALT"),
    ExtraKey("◀", "\u001b[D", popup = "Home"),
    ExtraKey("▼", "\u001b[B", popup = "PgDn"),
    ExtraKey("▶", "\u001b[C", popup = "End"),
    ExtraKey("⏎", "\r", popup = "^Z"),
    ExtraKey("u", popup = "\u0012"),  // u / Ctrl-R (redo)
    ExtraKey("⌨", code = "__KEYBOARD__"),
)
private val VIM_ROWS = listOf(VIM_ROW_1, VIM_ROW_2)

// ── Dev layout ─────────────────────────────────────────────
// More symbols: {}, [], (), <>, =, ;
private val DEV_ROW_1 = listOf(
    ExtraKey("CTRL", isModifier = true, popup = "^C", tooltipKey = "CTRL"),
    ExtraKey("ALT", isModifier = true, popup = "^R", tooltipKey = "ALT"),
    ExtraKey("▲", "\u001b[A", popup = "PgUp"),
    ExtraKey("TAB", "\t", popup = "@", tooltipKey = "TAB"),
    ExtraKey("{", popup = "}"),
    ExtraKey("[", popup = "]"),
    ExtraKey("(", popup = ")"),
    ExtraKey("<", popup = ">"),
)
private val DEV_ROW_2 = listOf(
    ExtraKey("ESC", "\u001b", popup = "exit", tooltipKey = "ESC"),
    ExtraKey("◀", "\u001b[D", popup = "Home"),
    ExtraKey("▼", "\u001b[B", popup = "PgDn"),
    ExtraKey("▶", "\u001b[C", popup = "End"),
    ExtraKey(";", popup = ":"),
    ExtraKey("=", popup = "!="),
    ExtraKey("|", popup = "&"),
    ExtraKey("⏎", "\r", popup = "^Z"),
    ExtraKey("⌨", code = "__KEYBOARD__"),
)
private val DEV_ROWS = listOf(DEV_ROW_1, DEV_ROW_2)

// ── Minimal layout ─────────────────────────────────────────
// Just the essentials: CTRL, ALT, TAB, ESC, arrows, Enter.
private val MINIMAL_ROW_1 = listOf(
    ExtraKey("CTRL", isModifier = true, popup = "^C", tooltipKey = "CTRL"),
    ExtraKey("ALT", isModifier = true, popup = "^R", tooltipKey = "ALT"),
    ExtraKey("▲", "\u001b[A", popup = "PgUp"),
    ExtraKey("TAB", "\t", popup = "@", tooltipKey = "TAB"),
    ExtraKey("ESC", "\u001b", popup = "exit", tooltipKey = "ESC"),
)
private val MINIMAL_ROW_2 = listOf(
    ExtraKey("◀", "\u001b[D", popup = "Home"),
    ExtraKey("▼", "\u001b[B", popup = "PgDn"),
    ExtraKey("▶", "\u001b[C", popup = "End"),
    ExtraKey("⏎", "\r", popup = "^Z"),
    ExtraKey("⌨", code = "__KEYBOARD__"),
)
private val MINIMAL_ROWS = listOf(MINIMAL_ROW_1, MINIMAL_ROW_2)

// ── AI layout ──────────────────────────────────────────────
// Optimized for AI coding agents: Claude Code, Gemini CLI, Aider.
// Ctrl+O (transcript) is a dedicated key — the most used shortcut in Claude Code.
// Ctrl+B (scroll back) for reviewing long AI output on touch screens.
private val AI_ROW_1 = listOf(
    ExtraKey("ESC", "\u001b", popup = "exit", tooltipKey = "ESC"),
    ExtraKey("^O", Sequences.CTRL_O, popup = "^L"),
    ExtraKey("TAB", "\t", popup = "@", tooltipKey = "TAB"),
    ExtraKey("|", popup = "&"),
    ExtraKey("!", popup = "#"),
    ExtraKey("▲", "\u001b[A", popup = "PgUp"),
    ExtraKey("^C", Sequences.CTRL_C, popup = "^D"),
)
private val AI_ROW_2 = listOf(
    ExtraKey("CTRL", isModifier = true, popup = "^C", tooltipKey = "CTRL"),
    ExtraKey("ALT", isModifier = true, popup = "^R", tooltipKey = "ALT"),
    ExtraKey("^B", Sequences.CTRL_B, popup = "^F"),
    ExtraKey("/", popup = "\\"),
    ExtraKey("~", popup = "`"),
    ExtraKey("▼", "\u001b[B", popup = "PgDn"),
    ExtraKey("^Z", Sequences.CTRL_Z, popup = "^\\"),
    ExtraKey("⌨", code = "__KEYBOARD__"),
)
private val AI_ROWS = listOf(AI_ROW_1, AI_ROW_2)

/** Returns the key layout rows for the given style name. */
internal fun extraKeysRowsForStyle(style: String): List<List<ExtraKey>> = when (style) {
    "vim" -> VIM_ROWS
    "dev" -> DEV_ROWS
    "minimal" -> MINIMAL_ROWS
    "ai" -> AI_ROWS
    else -> DEFAULT_ROWS
}

/**
 * Two-row bar of shortcut keys rendered above the soft keyboard.
 *
 * @param onKey          Sends a raw key code string to the terminal session.
 * @param onCtrlToggle   Toggles the CTRL modifier.
 * @param onAltToggle    Toggles the ALT modifier.
 * @param ctrlActive     Whether CTRL is currently toggled on.
 * @param altActive      Whether ALT is currently toggled on.
 * @param hapticEnabled  Respect the user's haptic preference.
 * @param extraKeysStyle Layout style: "default", "vim", "dev", or "minimal".
 */
@Composable
fun ExtraKeysBar(
    onKey: (String) -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onKeyboardToggle: () -> Unit = {},
    onVoiceInput: (() -> Unit)? = null,
    onCameraOcr: (() -> Unit)? = null,
    onSplitHorizontal: (() -> Unit)? = null,
    onSplitVertical: (() -> Unit)? = null,
    onCloseSplitPane: (() -> Unit)? = null,
    hasSplitPanes: Boolean = false,
    ctrlActive: Boolean = false,
    altActive: Boolean = false,
    hapticEnabled: Boolean = true,
    extraKeysStyle: String = "default",
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val rows = remember(extraKeysStyle) { extraKeysRowsForStyle(extraKeysStyle) }

    // Use MaterialTheme surface colors with Gruvbox-compatible fallbacks.
    val barBackground = MaterialTheme.colorScheme.surfaceContainerLowest

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBackground)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEachIndexed { index, row ->
            ExtraKeyRow(row, onKey, onCtrlToggle, onAltToggle, onKeyboardToggle, ctrlActive, altActive, hapticEnabled, haptic,
                // Trailing buttons at the end of the last row
                trailingContent = if (index == rows.lastIndex) {
                    {
                        // Split pane button: tap = horizontal split, long press = vertical split
                        if (onSplitHorizontal != null) {
                            SplitPaneButton(
                                onSplitHorizontal = onSplitHorizontal,
                                onSplitVertical = onSplitVertical,
                                onCloseSplitPane = onCloseSplitPane,
                                hasSplitPanes = hasSplitPanes,
                                hapticEnabled = hapticEnabled,
                                haptic = haptic,
                            )
                        }
                        if (onCameraOcr != null) {
                            CameraOcrButton(onCameraOcr, hapticEnabled, haptic)
                        }
                        if (onVoiceInput != null) {
                            VoiceInputButton(onVoiceInput, hapticEnabled, haptic)
                        }
                    }
                } else null,
            )
        }
    }
}

/**
 * Microphone button for voice-to-text input.
 * Launches Android's SpeechRecognizer via the parent-provided callback.
 */
@Composable
private fun VoiceInputButton(
    onClick: () -> Unit,
    hapticEnabled: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface

    IconButton(
        onClick = {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = surfaceColor,
            contentColor = textColor,
        ),
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = stringResource(R.string.cd_voice_input),
        )
    }
}

/**
 * Camera button for OCR text recognition.
 * Opens the camera sheet to capture and paste text into the terminal.
 */
@Composable
private fun CameraOcrButton(
    onClick: () -> Unit,
    hapticEnabled: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface

    IconButton(
        onClick = {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = surfaceColor,
            contentColor = textColor,
        ),
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = stringResource(R.string.cd_camera_ocr),
        )
    }
}

/**
 * Split pane button for the extra keys bar.
 * Tap = horizontal split (side by side), Long press = vertical split (top/bottom).
 * When panes are already split, shows a close button instead on long press.
 */
@Composable
private fun SplitPaneButton(
    onSplitHorizontal: () -> Unit,
    onSplitVertical: (() -> Unit)?,
    onCloseSplitPane: (() -> Unit)?,
    hasSplitPanes: Boolean,
    hapticEnabled: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary
    var pressed by remember { mutableStateOf(false) }
    val currentOnSplit by rememberUpdatedState(onSplitHorizontal)
    val currentOnSplitV by rememberUpdatedState(onSplitVertical)
    val currentOnClose by rememberUpdatedState(onCloseSplitPane)

    Box(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hasSplitPanes) accentColor.copy(alpha = 0.2f) else surfaceColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        currentOnSplit()
                    },
                    onLongPress = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (hasSplitPanes) {
                            currentOnClose?.invoke()
                        } else {
                            currentOnSplitV?.invoke()
                        }
                    },
                )
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // Vertical bar icon for horizontal split; X when panes exist (long press to close)
            text = if (hasSplitPanes) "\u2573" else "\u2590\u258C",
            color = if (hasSplitPanes) accentColor else textColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
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
    trailingContent: (@Composable () -> Unit)? = null,
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
                            "^O" -> currentOnKey(Sequences.CTRL_O)
                            "^B" -> currentOnKey(Sequences.CTRL_B)
                            "^L" -> currentOnKey(Sequences.CTRL_L)
                            "^D" -> currentOnKey(Sequences.CTRL_D)
                            "^F" -> currentOnKey("\u0006")
                            "^\\" -> currentOnKey("\u001c")
                            else -> currentOnKey(key.popup)
                        }
                    }
                }
            )
        }

        trailingContent?.invoke()
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
                                    key.tooltipKey?.let { TooltipPreferences.incrementTooltipCount(context, it) }
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
