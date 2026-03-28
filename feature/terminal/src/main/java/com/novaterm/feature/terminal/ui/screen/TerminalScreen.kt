package com.novaterm.feature.terminal.ui.screen

import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalRenderer
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

@Composable
fun TerminalScreen(
    session: TerminalSession,
    modifier: Modifier = Modifier,
) {
    val viewClient = remember { NovaTermViewClient() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TerminalView(context, null).apply {
                setTextSize(14)
                setTerminalViewClient(viewClient)
                attachSession(session)
                requestFocus()
            }
        },
        update = { view ->
            if (view.currentSession != session) {
                view.attachSession(session)
            }
        }
    )
}

private class NovaTermViewClient : TerminalViewClient {

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent?) {}

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

    override fun onLongPress(event: MotionEvent?): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) {}

    override fun logWarn(tag: String?, message: String?) {}

    override fun logInfo(tag: String?, message: String?) {}

    override fun logDebug(tag: String?, message: String?) {}

    override fun logVerbose(tag: String?, message: String?) {}

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}

    override fun logStackTrace(tag: String?, e: Exception?) {}
}
