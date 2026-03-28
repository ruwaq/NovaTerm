package com.novaterm.feature.terminal.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SemanticZoneTrackerTest {

    private lateinit var tracker: SemanticZoneTracker
    private val completedBlocks = mutableListOf<Pair<String, Int?>>()

    @Before
    fun setup() {
        tracker = SemanticZoneTracker()
        tracker.onBlockComplete = { cmd, exitCode ->
            completedBlocks.add(cmd to exitCode)
        }
        completedBlocks.clear()
    }

    @Test
    fun `A marker creates prompt zone`() {
        tracker.onOsc133('A', null, row = 0, col = 0)
        assertEquals(1, tracker.zones.size)
        assertEquals(ZoneType.PROMPT, tracker.zones[0].type)
    }

    @Test
    fun `full command cycle creates 3 zones and emits block`() {
        // Shell prints prompt
        tracker.onOsc133('A', null, row = 0, col = 0)
        // User types command and presses Enter
        tracker.onOsc133('B', null, row = 0, col = 5)
        tracker.setCurrentCommand("ls -la")
        // Output begins
        tracker.onOsc133('C', null, row = 1, col = 0)
        // Command finishes
        tracker.onOsc133('D', "0", row = 10, col = 0)

        assertEquals(3, tracker.zones.size)
        assertEquals(ZoneType.PROMPT, tracker.zones[0].type)
        assertEquals(ZoneType.INPUT, tracker.zones[1].type)
        assertEquals(ZoneType.OUTPUT, tracker.zones[2].type)

        // Block completed callback fired
        assertEquals(1, completedBlocks.size)
        assertEquals("ls -la", completedBlocks[0].first)
        assertEquals(0, completedBlocks[0].second)
    }

    @Test
    fun `D marker passes exit code`() {
        tracker.onOsc133('A', null, row = 0, col = 0)
        tracker.onOsc133('B', null, row = 0, col = 5)
        tracker.setCurrentCommand("false")
        tracker.onOsc133('C', null, row = 1, col = 0)
        tracker.onOsc133('D', "1", row = 1, col = 0)

        assertEquals(1, completedBlocks[0].second)
    }

    @Test
    fun `getPromptPositions returns all prompt rows`() {
        tracker.onOsc133('A', null, row = 0, col = 0)
        tracker.onOsc133('B', null, row = 0, col = 5)
        tracker.onOsc133('C', null, row = 1, col = 0)
        tracker.onOsc133('D', "0", row = 5, col = 0)

        tracker.onOsc133('A', null, row = 6, col = 0)
        tracker.onOsc133('B', null, row = 6, col = 5)
        tracker.onOsc133('C', null, row = 7, col = 0)
        tracker.onOsc133('D', "0", row = 10, col = 0)

        val prompts = tracker.getPromptPositions()
        assertEquals(listOf(0, 6), prompts)
    }

    @Test
    fun `findZoneAt returns correct zone`() {
        tracker.onOsc133('A', null, row = 0, col = 0)
        tracker.onOsc133('B', null, row = 0, col = 5)
        tracker.onOsc133('C', null, row = 1, col = 0)
        tracker.onOsc133('D', "0", row = 5, col = 0)

        val zone = tracker.findZoneAt(3)
        assertNotNull(zone)
        assertEquals(ZoneType.OUTPUT, zone?.type)
    }

    @Test
    fun `clear removes all zones`() {
        tracker.onOsc133('A', null, row = 0, col = 0)
        tracker.onOsc133('B', null, row = 0, col = 5)
        tracker.clear()

        assertTrue(tracker.zones.isEmpty())
        assertEquals(emptyList<Int>(), tracker.getPromptPositions())
    }

    @Test
    fun `zones close properly when new zone starts`() {
        tracker.onOsc133('A', null, row = 0, col = 0)
        tracker.onOsc133('B', null, row = 0, col = 10)

        // First zone (prompt) should be closed at (0, 10)
        assertEquals(0, tracker.zones[0].endRow)
        assertEquals(10, tracker.zones[0].endCol)
    }

    @Test
    fun `D without command does not emit block`() {
        tracker.onOsc133('A', null, row = 0, col = 0)
        tracker.onOsc133('D', "0", row = 0, col = 0)

        assertTrue(completedBlocks.isEmpty())
    }
}
