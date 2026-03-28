package com.novaterm.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSchemesTest {

    @Test
    fun `all schemes have unique IDs`() {
        val ids = ColorSchemes.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all schemes have non-empty display names`() {
        ColorSchemes.ALL.forEach { scheme ->
            assertTrue("${scheme.id} has blank name", scheme.displayName.isNotBlank())
        }
    }

    @Test
    fun `default ID exists in ALL`() {
        assertTrue(ColorSchemes.ALL.any { it.id == ColorSchemes.DEFAULT_ID })
    }

    @Test
    fun `isDark returns true for dark schemes`() {
        assertTrue(ColorSchemes.isDark("gruvbox-dark"))
        assertTrue(ColorSchemes.isDark("catppuccin-mocha"))
        assertTrue(ColorSchemes.isDark("dracula"))
    }

    @Test
    fun `isDark returns false for light schemes`() {
        assertFalse(ColorSchemes.isDark("gruvbox-light"))
    }

    @Test
    fun `isDark defaults to true for unknown`() {
        assertTrue(ColorSchemes.isDark("nonexistent"))
    }

    @Test
    fun `displayName returns correct name`() {
        assertEquals("Gruvbox Dark", ColorSchemes.displayName("gruvbox-dark"))
        assertEquals("Catppuccin Mocha", ColorSchemes.displayName("catppuccin-mocha"))
    }

    @Test
    fun `displayName returns ID for unknown scheme`() {
        assertEquals("unknown-scheme", ColorSchemes.displayName("unknown-scheme"))
    }

    @Test
    fun `exactly 7 schemes defined`() {
        assertEquals(7, ColorSchemes.ALL.size)
    }
}
