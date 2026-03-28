package com.novaterm.feature.terminal.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
