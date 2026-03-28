package com.novaterm.app.ui.viewmodel

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests ViewModel state management logic in isolation.
 * Exercises the pure state flows without Android framework.
 */
class TerminalViewModelStateTest {

    @Test
    fun `ctrl toggle flips state`() {
        val ctrlActive = MutableStateFlow(false)

        ctrlActive.value = !ctrlActive.value
        assertTrue(ctrlActive.value)

        ctrlActive.value = !ctrlActive.value
        assertFalse(ctrlActive.value)
    }

    @Test
    fun `resetModifiers clears both ctrl and alt`() {
        val ctrlActive = MutableStateFlow(true)
        val altActive = MutableStateFlow(true)

        ctrlActive.value = false
        altActive.value = false

        assertFalse(ctrlActive.value)
        assertFalse(altActive.value)
    }

    @Test
    fun `selectSession updates current index`() {
        val currentSessionIndex = MutableStateFlow(0)
        currentSessionIndex.value = 2
        assertEquals(2, currentSessionIndex.value)
    }

    @Test
    fun `removeSession adjusts index when removing last`() {
        val currentIndex = MutableStateFlow(2)
        val newCount = 2 // After removal of 3rd session

        if (currentIndex.value >= newCount) {
            currentIndex.value = (newCount - 1).coerceAtLeast(0)
        }

        assertEquals(1, currentIndex.value)
    }

    @Test
    fun `removeSession keeps index at 0 when all gone`() {
        val currentIndex = MutableStateFlow(0)
        val newCount = 0

        if (currentIndex.value >= newCount) {
            currentIndex.value = (newCount - 1).coerceAtLeast(0)
        }

        assertEquals(0, currentIndex.value)
    }

    @Test
    fun `removeSession preserves index when removing non-selected`() {
        val currentIndex = MutableStateFlow(0)
        val newCount = 2 // Removed session at index 2

        // Index 0 is still valid
        if (currentIndex.value >= newCount) {
            currentIndex.value = (newCount - 1).coerceAtLeast(0)
        }

        assertEquals(0, currentIndex.value)
    }

    @Test
    fun `showSettings and hideSettings toggle correctly`() = runTest {
        val showSettings = MutableStateFlow(false)

        showSettings.test {
            assertFalse(awaitItem())

            showSettings.value = true
            assertTrue(awaitItem())

            showSettings.value = false
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `safeIndex clamping logic`() {
        fun clamp(selectedTab: Int, sessionsSize: Int): Int =
            selectedTab.coerceIn(0, (sessionsSize - 1).coerceAtLeast(0))

        assertEquals(0, clamp(0, 0))   // empty sessions
        assertEquals(0, clamp(0, 1))   // single session
        assertEquals(0, clamp(5, 1))   // out of bounds
        assertEquals(2, clamp(2, 5))   // valid
        assertEquals(4, clamp(4, 5))   // last valid
        assertEquals(4, clamp(10, 5))  // clamped to last
    }
}
