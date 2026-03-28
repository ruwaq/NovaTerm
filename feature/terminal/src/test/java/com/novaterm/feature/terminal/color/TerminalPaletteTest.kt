package com.novaterm.feature.terminal.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPaletteTest {

    private val ALL_SCHEME_IDS = listOf(
        "gruvbox-dark", "gruvbox-light", "catppuccin-mocha",
        "solarized-dark", "monokai", "nord", "dracula",
    )

    @Test
    fun `all schemes produce 16 ANSI colors`() {
        ALL_SCHEME_IDS.forEach { id ->
            val palette = TerminalPalettes.forScheme(id)
            assertEquals("Scheme $id should have 16 ANSI colors", 16, palette.ansi.size)
        }
    }

    @Test
    fun `all schemes have non-zero foreground and background`() {
        ALL_SCHEME_IDS.forEach { id ->
            val palette = TerminalPalettes.forScheme(id)
            assertTrue("Scheme $id foreground should not be 0", palette.foreground != 0)
            assertTrue("Scheme $id background should not be 0", palette.background != 0)
            assertTrue("Scheme $id cursor should not be 0", palette.cursor != 0)
        }
    }

    @Test
    fun `foreground and background are different`() {
        ALL_SCHEME_IDS.forEach { id ->
            val palette = TerminalPalettes.forScheme(id)
            assertTrue(
                "Scheme $id: fg and bg should differ",
                palette.foreground != palette.background,
            )
        }
    }

    @Test
    fun `unknown scheme falls back to gruvbox dark`() {
        val unknown = TerminalPalettes.forScheme("nonexistent")
        val gruvbox = TerminalPalettes.forScheme("gruvbox-dark")
        assertEquals(gruvbox, unknown)
    }

    @Test
    fun `gruvbox dark has warm colors (no pure blue)`() {
        val palette = TerminalPalettes.GRUVBOX_DARK
        // ANSI blue (index 4) should not be pure blue (0xFF0000FF)
        val blue = palette.ansi[4]
        val r = (blue shr 16) and 0xFF
        val g = (blue shr 8) and 0xFF
        assertTrue("Gruvbox blue should be warm (has green component)", g > 100)
    }

    @Test
    fun `catppuccin mocha has pastel colors`() {
        val palette = TerminalPalettes.CATPPUCCIN_MOCHA
        assertNotNull(palette)
        // Background should be dark
        val bgR = (palette.background shr 16) and 0xFF
        assertTrue("Catppuccin bg should be dark", bgR < 64)
    }

    @Test
    fun `palette equality works correctly`() {
        val a = TerminalPalettes.GRUVBOX_DARK
        val b = TerminalPalettes.forScheme("gruvbox-dark")
        assertEquals(a, b)
    }

    @Test
    fun `all 7 schemes are distinct`() {
        val palettes = ALL_SCHEME_IDS.map { TerminalPalettes.forScheme(it) }.toSet()
        assertEquals("All schemes should be unique", 7, palettes.size)
    }
}
