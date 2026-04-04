package com.novaterm.feature.terminal.ui.screen

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import com.novaterm.feature.terminal.color.TerminalPalettes
import com.novaterm.feature.terminal.semantic.SemanticZoneTracker
import com.novaterm.feature.terminal.url.EntityConfirmDialog
import com.novaterm.feature.terminal.url.UrlDetector
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

private const val TAG = "NovaTerm"

/**
 * Wraps the Termux [TerminalView] inside Compose.
 *
 * @param session         Active PTY session to display.
 * @param fontSize        Text size in sp (from preferences).
 * @param keepScreenOn    Whether the view should prevent display sleep.
 * @param ctrlActive      True when the CTRL extra-key modifier is toggled on.
 * @param altActive       True when the ALT extra-key modifier is toggled on.
 * @param backIsEscape    Map the hardware back button to ESC.
 * @param onModifiersConsumed Called after a key press that used CTRL/ALT so the
 *                            caller can reset the modifier state ("one-shot" behavior).
 */
@Composable
fun TerminalScreen(
    session: TerminalSession,
    fontSize: Int = 14,
    colorScheme: String = "gruvbox-dark",
    keepScreenOn: Boolean = false,
    ctrlActive: Boolean = false,
    altActive: Boolean = false,
    backIsEscape: Boolean = false,
    onModifiersConsumed: () -> Unit = {},
    onBlockComplete: ((command: String, exitCode: Int?) -> Unit)? = null,
    /** Called when Tab is pressed to check if a suggestion should be accepted instead. Returns the suggestion text or null. */
    onTabAcceptSuggestion: (() -> String?)? = null,
    onViewReady: ((TerminalView) -> Unit)? = null,
    /** Exposes prompt navigation to the parent. Call jumpToPrompt(delta) where delta is -1 (up) or +1 (down). */
    onPromptNavigatorReady: ((jumpToPrompt: (Int) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // URL detection state
    var detectedEntity by remember { mutableStateOf<UrlDetector.Entity?>(null) }

    // Semantic zone tracker for OSC 133 (prompt/input/output markers)
    val zoneTracker = remember { SemanticZoneTracker() }
    zoneTracker.onBlockComplete = onBlockComplete

    val viewClient = remember {
        NovaTermViewClient(
            ctrlActive = ctrlActive,
            altActive = altActive,
            backIsEscape = backIsEscape,
            onModifiersConsumed = onModifiersConsumed,
            onEntityDetected = { entity -> detectedEntity = entity },
            onTabAcceptSuggestion = onTabAcceptSuggestion,
        )
    }

    // Keep the client's mutable state in sync with Compose state, avoiding
    // recomposition of the entire AndroidView on every modifier toggle.
    viewClient.ctrlActive = ctrlActive
    viewClient.altActive = altActive
    viewClient.backIsEscape = backIsEscape
    viewClient.onModifiersConsumed = onModifiersConsumed
    viewClient.onEntityDetected = { entity -> detectedEntity = entity }
    viewClient.onTabAcceptSuggestion = onTabAcceptSuggestion

    // Reference to the native view for imperative updates.
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    // Track last applied values — keyed on session so they reset on tab switch
    var lastFontSize by remember(session) { mutableIntStateOf(fontSize) }
    var lastColorScheme by remember(session) { mutableStateOf(colorScheme) }

    // Scrollback indicator state
    var isScrolledUp by remember { mutableStateOf(false) }
    var scrollProgress by remember { mutableIntStateOf(100) }

    // Poll scroll state periodically (only when view exists, cancels on session change)
    // Key on session to ensure coroutine is cancelled when tab switches
    LaunchedEffect(session) {
        while (true) {
            val view = terminalViewRef.value
            if (view != null) {
                val scrolled = view.isScrolledUp
                isScrolledUp = scrolled
                if (scrolled) {
                    scrollProgress = view.scrollProgress
                }
            }
            // Slower polling (5Hz) - scroll indicator doesn't need real-time updates
            kotlinx.coroutines.delay(200)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp), // Balanced: readable without wasting columns
        factory = { context ->
            applyTerminalPalette(colorScheme)

            TerminalView(context, null).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                // Hardware acceleration for smoother rendering
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                // Convert sp to px for consistent sizing across densities
                val fontSizePx = (fontSize * context.resources.displayMetrics.scaledDensity).toInt()
                setTextSize(fontSizePx)
                setTerminalViewClient(viewClient)
                attachSession(session)
                setKeepScreenOn(keepScreenOn)
                setIsTerminalViewKeyLoggingEnabled(false)
                viewClient.terminalView = this
                terminalViewRef.value = this
                onViewReady?.invoke(this)

                // Expose prompt navigation to parent
                val tv = this
                onPromptNavigatorReady?.invoke { delta ->
                    val prompts = zoneTracker.getPromptPositions()
                    if (prompts.isEmpty()) return@invoke
                    val currentTop = tv.getTopRow()
                    val target = if (delta < 0) {
                        // Up: find the nearest prompt ABOVE current view
                        prompts.lastOrNull { it < currentTop } ?: prompts.first()
                    } else {
                        // Down: find the nearest prompt BELOW current view
                        prompts.firstOrNull { it > currentTop } ?: prompts.last()
                    }
                    tv.setTopRow(target)
                    tv.invalidate()
                }

                // Show keyboard automatically on first display
                post {
                    requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as? InputMethodManager
                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        },
        update = { view ->
            terminalViewRef.value = view

            // Attach new session when tab switches.
            if (view.currentSession != session) {
                view.attachSession(session)
            }

            // Apply font size changes only when actually changed (avoids recreating TerminalRenderer).
            if (fontSize != lastFontSize) {
                val fontSizePx = (fontSize * view.context.resources.displayMetrics.scaledDensity).toInt()
                view.setTextSize(fontSizePx)
                lastFontSize = fontSize
            }

            // Hot-reload color scheme when preference changes.
            if (colorScheme != lastColorScheme) {
                applyTerminalPalette(colorScheme)
                session.emulator?.mColors?.reset()
                view.invalidate()
                lastColorScheme = colorScheme
            }

            // Keep-screen-on follows preference.
            view.setKeepScreenOn(keepScreenOn)

            // Ensure the terminal has input focus so the soft keyboard targets it.
            if (!view.hasFocus()) {
                view.requestFocus()
            }
        }
    )

        // Scrollback position indicator (appears when scrolled up)
        AnimatedVisibility(
            visible = isScrolledUp,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                // Scroll position thumb
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxHeight(scrollProgress / 100f)
                        .width(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                )
            }
        }
    } // End of Box

    // Clean up when the session changes or the composable leaves composition.
    DisposableEffect(session) {
        onDispose {
            terminalViewRef.value?.setKeepScreenOn(false)
            viewClient.terminalView = null
            terminalViewRef.value = null
        }
    }

    // Entity confirmation dialog (URL, IP address, file path)
    detectedEntity?.let { entity ->
        EntityConfirmDialog(
            entity = entity,
            onDismiss = { detectedEntity = null },
        )
    }
}

/**
 * Apply a terminal color palette to the Termux engine.
 * Updates the static default colors so new and existing sessions pick them up.
 * Existing sessions need [TerminalColors.reset] + view invalidate for hot-reload.
 */
private fun applyTerminalPalette(schemeId: String) {
    val palette = TerminalPalettes.forScheme(schemeId)
    val cs = TerminalColors.COLOR_SCHEME.mDefaultColors

    // 16 ANSI colors
    palette.ansi.copyInto(cs, destinationOffset = 0)

    // Special colors
    cs[TextStyle.COLOR_INDEX_FOREGROUND] = palette.foreground
    cs[TextStyle.COLOR_INDEX_BACKGROUND] = palette.background
    cs[TextStyle.COLOR_INDEX_CURSOR]     = palette.cursor
}

/**
 * Bridge between the Compose-managed modifier state and the Termux
 * [TerminalView]'s key handling pipeline.
 *
 * The most critical methods are [readControlKey] and [readAltKey] --
 * [TerminalView.onKeyDown] calls them on every keypress to decide
 * whether to apply CTRL/ALT modifiers to the character. When they
 * return `true`, the view computes the correct escape sequence
 * (e.g. CTRL+C = 0x03).
 */
private class NovaTermViewClient(
    @Volatile var ctrlActive: Boolean,
    @Volatile var altActive: Boolean,
    @Volatile var backIsEscape: Boolean,
    var onModifiersConsumed: () -> Unit,
    var onEntityDetected: (UrlDetector.Entity) -> Unit = {},
    var onTabAcceptSuggestion: (() -> String?)? = null,
    var terminalView: TerminalView? = null,
) : TerminalViewClient {

    // ── Modifier keys (THE critical fix) ───────────────────────────
    // TerminalView reads these on every keypress to decide whether
    // CTRL/ALT should modify the character being typed.

    override fun readControlKey(): Boolean {
        val active = ctrlActive
        if (active) onModifiersConsumed()
        return active
    }

    override fun readAltKey(): Boolean {
        val active = altActive
        if (active) onModifiersConsumed()
        return active
    }

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    // ── Configuration ──────────────────────────────────────────────

    override fun shouldBackButtonBeMappedToEscape(): Boolean = backIsEscape

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    // ── Events ─────────────────────────────────────────────────────

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent?) {
        val view = terminalView ?: return

        // Try to detect a URL under the tap position
        try {
            val emulator = view.currentSession?.emulator
            if (emulator != null && e != null) {
                // Get the word at tap position from the terminal buffer
                val screen = emulator.screen
                val col = view.getCursorX(e.x).coerceIn(0, emulator.mColumns - 1)
                val row = view.getCursorY(e.y) + view.topRow
                val rowText = screen.getSelectedText(0, row, emulator.mColumns, row)?.trim()

                if (rowText != null) {
                    val entity = UrlDetector.findEntityAt(rowText, col)
                    if (entity != null) {
                        onEntityDetected(entity)
                        return
                    }
                }
            }
        } catch (ex: Exception) {
            Log.w(TAG, "URL detection failed", ex)
        }

        // No URL found: show keyboard (default behavior)
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
        // Shift+Enter → send newline without submit (critical for Claude Code multiline)
        if (keyCode == KeyEvent.KEYCODE_ENTER && e?.isShiftPressed == true) {
            session?.write("\n")
            return true
        }
        // Tab → accept suggestion if one is active (→ key also works, like Fish shell)
        if (keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            val accepted = onTabAcceptSuggestion?.invoke()
            if (!accepted.isNullOrEmpty()) {
                session?.write(accepted)
                return true
            }
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

    override fun onLongPress(event: MotionEvent?): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

    override fun onEmulatorSet() {}

    // ── Logging (route to Android logcat) ──────────────────────────

    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: TAG, message ?: "")
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: TAG, message ?: "")
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag ?: TAG, message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag ?: TAG, message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag ?: TAG, message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: TAG, "Exception", e)
    }
}
