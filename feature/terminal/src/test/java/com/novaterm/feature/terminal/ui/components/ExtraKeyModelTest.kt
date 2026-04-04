package com.novaterm.feature.terminal.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraKeyModelTest {

    @Test
    fun `default code equals label`() {
        val key = ExtraKey(label = "/")
        assertEquals("/", key.code)
    }

    @Test
    fun `custom code overrides label`() {
        val key = ExtraKey(label = "ESC", code = "\u001b")
        assertEquals("\u001b", key.code)
        assertEquals("ESC", key.label)
    }

    @Test
    fun `modifier key has isModifier true`() {
        val ctrl = ExtraKey("CTRL", isModifier = true)
        assertTrue(ctrl.isModifier)
    }

    @Test
    fun `non-modifier key has isModifier false by default`() {
        val tab = ExtraKey("TAB", "\t")
        assertFalse(tab.isModifier)
    }

    @Test
    fun `popup is null by default`() {
        val key = ExtraKey("ENT", "\r")
        assertNull(key.popup)
    }

    @Test
    fun `popup can be set`() {
        val key = ExtraKey("UP", "\u001b[A", popup = "PgUp")
        assertEquals("PgUp", key.popup)
    }

    @Test
    fun `data class equality works`() {
        val a = ExtraKey("ESC", "\u001b", popup = "exit")
        val b = ExtraKey("ESC", "\u001b", popup = "exit")
        assertEquals(a, b)
    }

    @Test
    fun `data class copy preserves fields`() {
        val original = ExtraKey("CTRL", isModifier = true, popup = "^C")
        val copy = original.copy(label = "ALT")
        assertEquals("ALT", copy.label)
        assertTrue(copy.isModifier)
        assertEquals("^C", copy.popup)
    }

    // ── extraKeysRowsForStyle tests ──────────────────────────

    @Test
    fun `default style returns two rows`() {
        val rows = extraKeysRowsForStyle("default")
        assertEquals(2, rows.size)
    }

    @Test
    fun `vim style returns two rows with ESC first`() {
        val rows = extraKeysRowsForStyle("vim")
        assertEquals(2, rows.size)
        assertEquals("ESC", rows[0][0].label)
    }

    @Test
    fun `dev style has bracket keys`() {
        val rows = extraKeysRowsForStyle("dev")
        val allLabels = rows.flatten().map { it.label }
        assertTrue("{" in allLabels)
        assertTrue("[" in allLabels)
        assertTrue("(" in allLabels)
    }

    @Test
    fun `minimal style has fewer keys than default`() {
        val defaultKeys = extraKeysRowsForStyle("default").flatten()
        val minimalKeys = extraKeysRowsForStyle("minimal").flatten()
        assertTrue(minimalKeys.size < defaultKeys.size)
    }

    @Test
    fun `unknown style falls back to default`() {
        val rows = extraKeysRowsForStyle("nonexistent")
        val defaultRows = extraKeysRowsForStyle("default")
        assertEquals(defaultRows, rows)
    }

    @Test
    fun `all styles have CTRL modifier`() {
        listOf("default", "vim", "dev", "minimal").forEach { style ->
            val keys = extraKeysRowsForStyle(style).flatten()
            val ctrl = keys.find { it.label == "CTRL" }
            assertNotNull("Style '$style' missing CTRL", ctrl)
            assertTrue(ctrl!!.isModifier)
        }
    }

    @Test
    fun `all styles have keyboard toggle`() {
        listOf("default", "vim", "dev", "minimal").forEach { style ->
            val keys = extraKeysRowsForStyle(style).flatten()
            val kbd = keys.find { it.code == "__KEYBOARD__" }
            assertNotNull("Style '$style' missing keyboard toggle", kbd)
        }
    }
}
