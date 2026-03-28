package com.novaterm.feature.terminal.ui.screen

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
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
    keepScreenOn: Boolean = false,
    ctrlActive: Boolean = false,
    altActive: Boolean = false,
    backIsEscape: Boolean = false,
    onModifiersConsumed: () -> Unit = {},
    onViewReady: ((TerminalView) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val viewClient = remember {
        NovaTermViewClient(
            ctrlActive = false,
            altActive = false,
            backIsEscape = false,
            onModifiersConsumed = onModifiersConsumed,
        )
    }

    // Keep the client's mutable state in sync with Compose state, avoiding
    // recomposition of the entire AndroidView on every modifier toggle.
    viewClient.ctrlActive = ctrlActive
    viewClient.altActive = altActive
    viewClient.backIsEscape = backIsEscape
    viewClient.onModifiersConsumed = onModifiersConsumed

    // Reference to the native view for imperative updates.
    val terminalViewRef = remember { androidx.compose.runtime.mutableStateOf<TerminalView?>(null) }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp), // Breathing room like Termux
        factory = { context ->
            // Apply NovaTerm color scheme to the terminal engine (once)
            applyNovaTermColors()

            TerminalView(context, null).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setTextSize(fontSize)
                setTerminalViewClient(viewClient)
                attachSession(session)
                setKeepScreenOn(keepScreenOn)
                setIsTerminalViewKeyLoggingEnabled(false)
                viewClient.terminalView = this
                terminalViewRef.value = this
                onViewReady?.invoke(this)

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

            // Apply font size changes.
            view.setTextSize(fontSize)

            // Keep-screen-on follows preference.
            view.setKeepScreenOn(keepScreenOn)

            // Ensure the terminal has input focus so the soft keyboard targets it.
            if (!view.hasFocus()) {
                view.requestFocus()
            }
        }
    )

    // Clean up when the composable leaves the composition.
    DisposableEffect(Unit) {
        onDispose {
            terminalViewRef.value?.setKeepScreenOn(false)
            terminalViewRef.value = null
        }
    }
}

/**
 * Apply NovaTerm's color scheme to the Termux terminal engine.
 * Based on the user's optimized Termux "Gruvbox Soft Dark - Eye Comfort"
 * configuration. Warm tones, no harsh blues, AMOLED friendly.
 */
private fun applyNovaTermColors() {
    val cs = TerminalColors.COLOR_SCHEME.mDefaultColors

    // 16 ANSI colors (from Termux colors.properties)
    cs[0]  = 0xFF3C3836.toInt()  // black (background)
    cs[1]  = 0xFFEA6962.toInt()  // red
    cs[2]  = 0xFFA9B665.toInt()  // green
    cs[3]  = 0xFFD8A657.toInt()  // yellow
    cs[4]  = 0xFF7DAEA3.toInt()  // blue (warm cyan)
    cs[5]  = 0xFFD3869B.toInt()  // magenta
    cs[6]  = 0xFF89B482.toInt()  // cyan
    cs[7]  = 0xFFA89984.toInt()  // white (dim)
    cs[8]  = 0xFF928374.toInt()  // bright black (gray)
    cs[9]  = 0xFFEA6962.toInt()  // bright red
    cs[10] = 0xFFB8BB26.toInt()  // bright green
    cs[11] = 0xFFFABD2F.toInt()  // bright yellow
    cs[12] = 0xFF83A598.toInt()  // bright blue (warm teal)
    cs[13] = 0xFFD3869B.toInt()  // bright magenta
    cs[14] = 0xFF8EC07C.toInt()  // bright cyan
    cs[15] = 0xFFEBDBB2.toInt()  // bright white

    // Special colors
    cs[TextStyle.COLOR_INDEX_FOREGROUND] = 0xFFEBDBB2.toInt()  // foreground
    cs[TextStyle.COLOR_INDEX_BACKGROUND] = 0xFF3C3836.toInt()  // background
    cs[TextStyle.COLOR_INDEX_CURSOR]     = 0xFFFABD2F.toInt()  // cursor (yellow)
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
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false

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
