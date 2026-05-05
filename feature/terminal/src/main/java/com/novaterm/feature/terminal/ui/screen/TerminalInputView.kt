package com.novaterm.feature.terminal.ui.screen

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.novaterm.terminal.TerminalSession

/**
 * Invisible view that proxies software keyboard (IME) input to a [TerminalSession].
 * Used by [GpuTerminalScreen] to receive text input from software keyboards
 * since [AndroidExternalSurface] does not natively support IME.
 */
class TerminalInputView(context: Context) : View(context) {
    var terminalSession: TerminalSession? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val session = terminalSession ?: return null
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection(this, session)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && hasFocus()) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}

/**
 * [BaseInputConnection] that forwards IME events to a [TerminalSession].
 */
class TerminalInputConnection(
    view: View,
    private val session: TerminalSession
) : BaseInputConnection(view, true) {

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        super.commitText(text, newCursorPosition)
        val content = getEditable()
        if (content != null && content.isNotEmpty()) {
            sendText(content.toString())
            content.clear()
        }
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val delBytes = byteArrayOf(0x7F)
        if (beforeLength > 0) {
            repeat(beforeLength) {
                session.write(delBytes, 0, 1)
            }
        } else if (beforeLength == 0 && afterLength == 0) {
            // Some IMEs send (0,0) as backspace
            session.write(delBytes, 0, 1)
        }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        event?.let {
            if (it.action == KeyEvent.ACTION_DOWN) {
                val bytes = keyEventToBytes(it)
                if (bytes != null) {
                    session.write(bytes, 0, bytes.size)
                }
            }
        }
        return true
    }

    private fun sendText(text: String) {
        // Terminals expect \r for enter, not \n
        val terminalText = text.replace('\n', '\r')
        val bytes = terminalText.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty()) {
            session.write(bytes, 0, bytes.size)
        }
    }
}
