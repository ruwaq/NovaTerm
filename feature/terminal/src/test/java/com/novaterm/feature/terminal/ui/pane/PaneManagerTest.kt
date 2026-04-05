package com.novaterm.feature.terminal.ui.pane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaneManagerTest {

    private lateinit var manager: PaneManager

    @Before
    fun setUp() {
        manager = PaneManager()
    }

    @Test
    fun `init creates a single leaf node`() {
        manager.init(0)
        val layout = manager.getCurrentLayout()
        assertNotNull(layout)
        assertTrue(layout is PaneNode.Leaf)
        assertEquals(0, (layout as PaneNode.Leaf).sessionIndex)
        assertEquals(0, manager.getFocusedPane())
    }

    @Test
    fun `hasSplit returns false for single pane`() {
        manager.init(0)
        assertFalse(manager.hasSplit())
    }

    @Test
    fun `splitFocusedPane creates a horizontal split`() {
        manager.init(0)
        val result = manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1)
        assertTrue(result)
        assertTrue(manager.hasSplit())

        val layout = manager.getCurrentLayout()
        assertTrue(layout is PaneNode.Split)
        val split = layout as PaneNode.Split
        assertEquals(SplitDirection.HORIZONTAL, split.direction)
        assertEquals(0.5f, split.ratio)
        assertEquals(0, (split.first as PaneNode.Leaf).sessionIndex)
        assertEquals(1, (split.second as PaneNode.Leaf).sessionIndex)
    }

    @Test
    fun `splitFocusedPane creates a vertical split`() {
        manager.init(0)
        val result = manager.splitFocusedPane(SplitDirection.VERTICAL, 1)
        assertTrue(result)

        val split = manager.getCurrentLayout() as PaneNode.Split
        assertEquals(SplitDirection.VERTICAL, split.direction)
    }

    @Test
    fun `focus stays on original pane after split`() {
        manager.init(0)
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1)
        assertEquals(0, manager.getFocusedPane())
    }

    @Test
    fun `can split up to MAX_PANES`() {
        manager.init(0)
        assertTrue(manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1))  // 2 panes
        assertTrue(manager.splitFocusedPane(SplitDirection.VERTICAL, 2))    // 3 panes
        assertTrue(manager.splitFocusedPane(SplitDirection.HORIZONTAL, 3))  // 4 panes
        assertFalse(manager.splitFocusedPane(SplitDirection.HORIZONTAL, 4)) // Rejected: max 4
    }

    @Test
    fun `allSessionIndices returns all leaves in order`() {
        manager.init(0)
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1)
        val layout = manager.getCurrentLayout()!!
        assertEquals(listOf(0, 1), layout.allSessionIndices())
    }

    @Test
    fun `leafCount returns correct count`() {
        manager.init(0)
        assertEquals(1, manager.getCurrentLayout()!!.leafCount())
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1)
        assertEquals(2, manager.getCurrentLayout()!!.leafCount())
    }

    @Test
    fun `closePane collapses tree to sibling`() {
        manager.init(0)
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1)

        val remaining = manager.closePane(1)
        assertNotNull(remaining)
        assertEquals(listOf(0), remaining)

        val layout = manager.getCurrentLayout()
        assertTrue(layout is PaneNode.Leaf)
        assertEquals(0, (layout as PaneNode.Leaf).sessionIndex)
    }

    @Test
    fun `closePane updates focus when focused pane is closed`() {
        manager.init(0)
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1)
        manager.setFocusedPane(1)
        assertEquals(1, manager.getFocusedPane())

        manager.closePane(1)
        assertEquals(0, manager.getFocusedPane())
    }

    @Test
    fun `closePane on single leaf returns null`() {
        manager.init(0)
        val result = manager.closePane(0)
        assertNull(result)
    }

    @Test
    fun `adjustIndicesAfterRemoval decrements higher indices`() {
        manager.init(2)
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 4)

        manager.adjustIndicesAfterRemoval(1) // Removing session 1 (not in this pane)

        val layout = manager.getCurrentLayout()!!
        val indices = layout.allSessionIndices()
        assertEquals(listOf(1, 3), indices) // 2->1, 4->3
    }

    @Test
    fun `adjustIndicesAfterRemoval does not change lower indices`() {
        manager.init(0)
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1)

        manager.adjustIndicesAfterRemoval(5) // Removing index higher than any pane

        val indices = manager.getCurrentLayout()!!.allSessionIndices()
        assertEquals(listOf(0, 1), indices) // Unchanged
    }

    @Test
    fun `setFocusedPane changes focus`() {
        manager.init(0)
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1)
        manager.setFocusedPane(1)
        assertEquals(1, manager.getFocusedPane())
    }

    @Test
    fun `nested splits work correctly`() {
        manager.init(0)
        manager.splitFocusedPane(SplitDirection.HORIZONTAL, 1) // [0 | 1]
        manager.setFocusedPane(1)
        manager.splitFocusedPane(SplitDirection.VERTICAL, 2) // [0 | [1 / 2]]

        val layout = manager.getCurrentLayout()!!
        assertEquals(3, layout.leafCount())
        assertEquals(listOf(0, 1, 2), layout.allSessionIndices())

        // Check structure
        assertTrue(layout is PaneNode.Split)
        val root = layout as PaneNode.Split
        assertEquals(SplitDirection.HORIZONTAL, root.direction)
        assertTrue(root.first is PaneNode.Leaf)
        assertTrue(root.second is PaneNode.Split)
        val nested = root.second as PaneNode.Split
        assertEquals(SplitDirection.VERTICAL, nested.direction)
    }
}
