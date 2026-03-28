package com.novaterm.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalConfigTest {

    @Test
    fun `default config has sensible values`() {
        val config = TerminalConfig()
        assertEquals(18, config.fontSize)
        assertEquals("monospace", config.fontFamily)
        assertEquals(ColorScheme.GRUVBOX_DARK, config.colorScheme)
        assertEquals(10_000, config.scrollbackLines)
        assertFalse(config.keepScreenOn)
        assertTrue(config.hapticFeedback)
        assertTrue(config.bellEnabled)
        assertTrue(config.showExtraKeys)
        assertTrue(config.backIsEscape)
        assertEquals("xterm-256color", config.terminalType)
    }

    @Test
    fun `font size at lower bound is valid`() {
        val config = TerminalConfig(fontSize = 8)
        assertEquals(8, config.fontSize)
    }

    @Test
    fun `font size at upper bound is valid`() {
        val config = TerminalConfig(fontSize = 32)
        assertEquals(32, config.fontSize)
    }

    @Test
    fun `font size below range throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalConfig(fontSize = 7)
        }
    }

    @Test
    fun `font size above range throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalConfig(fontSize = 33)
        }
    }

    @Test
    fun `negative scrollback throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalConfig(scrollbackLines = -1)
        }
    }

    @Test
    fun `zero scrollback is valid`() {
        val config = TerminalConfig(scrollbackLines = 0)
        assertEquals(0, config.scrollbackLines)
    }

    @Test
    fun `copy with changes preserves unmodified fields`() {
        val original = TerminalConfig()
        val modified = original.copy(fontSize = 20, keepScreenOn = true)
        assertEquals(20, modified.fontSize)
        assertTrue(modified.keepScreenOn)
        assertEquals(original.colorScheme, modified.colorScheme)
        assertEquals(original.scrollbackLines, modified.scrollbackLines)
        assertEquals(original.hapticFeedback, modified.hapticFeedback)
    }

    @Test
    fun `all color schemes have display names`() {
        ColorScheme.entries.forEach { scheme ->
            assertTrue(
                "ColorScheme $scheme has empty displayName",
                scheme.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun `font size range is correctly defined`() {
        assertEquals(8, TerminalConfig.FONT_SIZE_RANGE.first)
        assertEquals(32, TerminalConfig.FONT_SIZE_RANGE.last)
    }
}
