package com.novaterm.feature.terminal.ui.screen

import android.view.KeyEvent

/**
 * Convert an Android KeyEvent to terminal bytes.
 * Basic mapping for common keys.
 */
internal fun keyEventToBytes(event: KeyEvent): ByteArray? {
    return when (event.keyCode) {
        KeyEvent.KEYCODE_ENTER -> byteArrayOf(0x0D)
        KeyEvent.KEYCODE_DEL -> byteArrayOf(0x7F)
        KeyEvent.KEYCODE_FORWARD_DEL -> byteArrayOf(0x1B.toByte(), 0x5B, 0x33, 0x7E)
        KeyEvent.KEYCODE_TAB -> byteArrayOf(0x09)
        KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(0x1B.toByte())
        KeyEvent.KEYCODE_DPAD_UP -> byteArrayOf(0x1B.toByte(), 0x5B, 0x41)
        KeyEvent.KEYCODE_DPAD_DOWN -> byteArrayOf(0x1B.toByte(), 0x5B, 0x42)
        KeyEvent.KEYCODE_DPAD_RIGHT -> byteArrayOf(0x1B.toByte(), 0x5B, 0x43)
        KeyEvent.KEYCODE_DPAD_LEFT -> byteArrayOf(0x1B.toByte(), 0x5B, 0x44)
        KeyEvent.KEYCODE_MOVE_HOME -> byteArrayOf(0x1B.toByte(), 0x5B, 0x48)
        KeyEvent.KEYCODE_MOVE_END -> byteArrayOf(0x1B.toByte(), 0x5B, 0x46)
        KeyEvent.KEYCODE_PAGE_UP -> byteArrayOf(0x1B.toByte(), 0x5B, 0x35, 0x7E)
        KeyEvent.KEYCODE_PAGE_DOWN -> byteArrayOf(0x1B.toByte(), 0x5B, 0x36, 0x7E)
        else -> {
            val c = event.unicodeChar
            if (c != 0) {
                val bytes = c.toString().toByteArray(Charsets.UTF_8)
                // Apply CTRL modifier
                if (event.isCtrlPressed && c in 'a'.code..'z'.code) {
                    byteArrayOf((c - 'a'.code + 1).toByte())
                } else {
                    bytes
                }
            } else {
                null
            }
        }
    }
}
