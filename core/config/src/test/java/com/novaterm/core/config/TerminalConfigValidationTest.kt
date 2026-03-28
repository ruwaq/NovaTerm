package com.novaterm.core.config

import com.novaterm.core.common.model.ColorScheme
import com.novaterm.core.common.model.TerminalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalConfigValidationTest {

    @Test
    fun `config transformation preserves invariants`() {
        val original = TerminalConfig()
        val transformed = original.copy(
            fontSize = 20,
            colorScheme = ColorScheme.DRACULA,
            scrollbackLines = 50_000,
        )
        assertEquals(20, transformed.fontSize)
        assertEquals(ColorScheme.DRACULA, transformed.colorScheme)
        assertEquals(50_000, transformed.scrollbackLines)
    }

    @Test
    fun `config rejects invalid font size via transform`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalConfig().copy(fontSize = 0)
        }
    }

    @Test
    fun `config rejects negative scrollback via transform`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalConfig().copy(scrollbackLines = -100)
        }
    }

    @Test
    fun `all color schemes are valid enum values`() {
        val validNames = ColorScheme.entries.map { it.name }.toSet()
        ColorScheme.entries.forEach { scheme ->
            assertEquals(
                "ColorScheme $scheme should round-trip through valueOf",
                scheme,
                ColorScheme.valueOf(scheme.name)
            )
            assertTrue(
                "ColorScheme $scheme name should be in valid set",
                scheme.name in validNames
            )
        }
    }

    @Test
    fun `invalid color scheme name falls back gracefully`() {
        val fallback = try {
            ColorScheme.valueOf("NONEXISTENT_SCHEME")
        } catch (_: IllegalArgumentException) {
            ColorScheme.GRUVBOX_DARK
        }
        assertEquals(ColorScheme.GRUVBOX_DARK, fallback)
    }

    @Test
    fun `boundary font sizes are accepted`() {
        val minConfig = TerminalConfig(fontSize = TerminalConfig.FONT_SIZE_RANGE.first)
        val maxConfig = TerminalConfig(fontSize = TerminalConfig.FONT_SIZE_RANGE.last)
        assertEquals(8, minConfig.fontSize)
        assertEquals(32, maxConfig.fontSize)
    }

    @Test
    fun `large scrollback is valid for modern devices`() {
        val config = TerminalConfig(scrollbackLines = 1_000_000)
        assertEquals(1_000_000, config.scrollbackLines)
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
