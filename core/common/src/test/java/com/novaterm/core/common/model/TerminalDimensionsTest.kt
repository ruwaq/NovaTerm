package com.novaterm.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalDimensionsTest {

    @Test
    fun `valid dimensions are created successfully`() {
        val dim = TerminalDimensions(rows = 24, columns = 80)
        assertEquals(24, dim.rows)
        assertEquals(80, dim.columns)
    }

    @Test
    fun `large dimensions for high-res displays`() {
        val dim = TerminalDimensions(rows = 120, columns = 300)
        assertEquals(120, dim.rows)
        assertEquals(300, dim.columns)
    }

    @Test
    fun `minimum valid dimensions`() {
        val dim = TerminalDimensions(rows = 1, columns = 1)
        assertEquals(1, dim.rows)
        assertEquals(1, dim.columns)
    }

    @Test
    fun `zero rows throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalDimensions(rows = 0, columns = 80)
        }
    }

    @Test
    fun `negative rows throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalDimensions(rows = -1, columns = 80)
        }
    }

    @Test
    fun `zero columns throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalDimensions(rows = 24, columns = 0)
        }
    }

    @Test
    fun `negative columns throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalDimensions(rows = 24, columns = -5)
        }
    }

    @Test
    fun `equality is structural`() {
        val a = TerminalDimensions(24, 80)
        val b = TerminalDimensions(24, 80)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `copy preserves valid state`() {
        val original = TerminalDimensions(24, 80)
        val resized = original.copy(rows = 48, columns = 120)
        assertEquals(48, resized.rows)
        assertEquals(120, resized.columns)
    }
}
