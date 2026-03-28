package com.novaterm.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the resolveKeyInput function that applies CTRL/ALT modifiers
 * to extra key inputs before sending to the terminal.
 */
class KeyInputTest {

    // Mirror of the private resolveKeyInput from NovaTermApp.kt
    private fun resolveKeyInput(code: String, ctrlActive: Boolean, altActive: Boolean): String {
        if (code.length != 1) return code

        return when {
            ctrlActive -> {
                val c = code[0]
                if (c in 'a'..'z' || c in 'A'..'Z') {
                    (c.uppercaseChar() - 'A' + 1).toChar().toString()
                } else {
                    code
                }
            }
            altActive -> "\u001b$code"
            else -> code
        }
    }

    @Test
    fun `no modifiers returns code unchanged`() {
        assertEquals("a", resolveKeyInput("a", ctrlActive = false, altActive = false))
        assertEquals("/", resolveKeyInput("/", ctrlActive = false, altActive = false))
    }

    @Test
    fun `ctrl+a produces SOH (0x01)`() {
        val result = resolveKeyInput("a", ctrlActive = true, altActive = false)
        assertEquals("\u0001", result)
    }

    @Test
    fun `ctrl+c produces ETX (0x03)`() {
        val result = resolveKeyInput("c", ctrlActive = true, altActive = false)
        assertEquals("\u0003", result)
    }

    @Test
    fun `ctrl+z produces SUB (0x1A)`() {
        val result = resolveKeyInput("z", ctrlActive = true, altActive = false)
        assertEquals("\u001a", result)
    }

    @Test
    fun `ctrl+uppercase works same as lowercase`() {
        assertEquals(
            resolveKeyInput("c", ctrlActive = true, altActive = false),
            resolveKeyInput("C", ctrlActive = true, altActive = false),
        )
    }

    @Test
    fun `ctrl+non-alpha returns code unchanged`() {
        assertEquals("/", resolveKeyInput("/", ctrlActive = true, altActive = false))
        assertEquals("1", resolveKeyInput("1", ctrlActive = true, altActive = false))
    }

    @Test
    fun `alt prepends ESC`() {
        val result = resolveKeyInput("a", ctrlActive = false, altActive = true)
        assertEquals("\u001ba", result)
    }

    @Test
    fun `alt+slash sends ESC+slash`() {
        val result = resolveKeyInput("/", ctrlActive = false, altActive = true)
        assertEquals("\u001b/", result)
    }

    @Test
    fun `multi-char code is returned unchanged regardless of modifiers`() {
        val escape = "\u001b[A" // Up arrow
        assertEquals(escape, resolveKeyInput(escape, ctrlActive = true, altActive = false))
        assertEquals(escape, resolveKeyInput(escape, ctrlActive = false, altActive = true))
    }

    @Test
    fun `ctrl takes precedence over alt when both active`() {
        val result = resolveKeyInput("c", ctrlActive = true, altActive = true)
        assertEquals("\u0003", result) // CTRL wins
    }
}
