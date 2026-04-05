package com.novaterm.feature.terminal.ui.pane

/**
 * Built-in terminal multiplexer — binary tree of split panes.
 *
 * Each tab can optionally have a pane layout. When no split exists, a single
 * [PaneNode.Leaf] wraps the session index. Splitting creates a [PaneNode.Split]
 * with two leaves, each pointing to an independent session with its own PTY.
 *
 * Maximum 4 panes per tab to keep mobile usable on a 6.83" screen.
 */

/** Direction of a pane split. */
enum class SplitDirection {
    /** Side by side (left | right). */
    HORIZONTAL,

    /** Stacked (top / bottom). */
    VERTICAL,
}

/** Binary tree node representing either a single terminal or a split. */
sealed class PaneNode {
    /** A single terminal pane displaying the session at [sessionIndex]. */
    data class Leaf(val sessionIndex: Int) : PaneNode()

    /**
     * A split containing two child nodes.
     * @param direction Whether children are arranged horizontally or vertically.
     * @param ratio Weight of the first child (0..1). Fixed at 0.5 for now.
     * @param first Left/top child.
     * @param second Right/bottom child.
     */
    data class Split(
        val direction: SplitDirection,
        val ratio: Float,
        val first: PaneNode,
        val second: PaneNode,
    ) : PaneNode()
}

/** Count the number of leaves (panes) in a tree. */
fun PaneNode.leafCount(): Int = when (this) {
    is PaneNode.Leaf -> 1
    is PaneNode.Split -> first.leafCount() + second.leafCount()
}

/** Collect all session indices from leaves in order. */
fun PaneNode.allSessionIndices(): List<Int> = when (this) {
    is PaneNode.Leaf -> listOf(sessionIndex)
    is PaneNode.Split -> first.allSessionIndices() + second.allSessionIndices()
}

/**
 * Manages the pane tree for a single tab.
 *
 * Holds a mutable pane layout and tracks which pane has focus.
 * The caller (ViewModel) is responsible for creating/destroying sessions
 * and passing the new session index when splitting.
 */
class PaneManager {

    companion object {
        /** Maximum panes per tab (keeps mobile screens usable). */
        const val MAX_PANES = 4
    }

    /** Current pane tree. Null means no split — just a regular single-pane tab. */
    private var _root: PaneNode? = null

    /** Session index of the currently focused pane. */
    private var _focusedSessionIndex: Int = -1

    // ── Public API ────────────────────────────────────────────

    /**
     * Initialize the manager for a tab with the given session index.
     * Called when a tab is first created or when entering split mode.
     */
    fun init(sessionIndex: Int) {
        if (_root == null) {
            _root = PaneNode.Leaf(sessionIndex)
            _focusedSessionIndex = sessionIndex
        }
    }

    /** Returns the current pane tree, or null if not initialized. */
    fun getCurrentLayout(): PaneNode? = _root

    /** Returns true if the tab has more than one pane. */
    fun hasSplit(): Boolean {
        val root = _root ?: return false
        return root is PaneNode.Split
    }

    /** Returns the focused session index. */
    fun getFocusedPane(): Int = _focusedSessionIndex

    /** Set focus to a specific session index. */
    fun setFocusedPane(sessionIndex: Int) {
        _focusedSessionIndex = sessionIndex
    }

    /**
     * Split the currently focused pane in the given direction.
     *
     * @param direction Horizontal or vertical split.
     * @param newSessionIndex The session index for the new pane (caller creates the session).
     * @return True if the split was performed, false if max panes reached.
     */
    fun splitFocusedPane(direction: SplitDirection, newSessionIndex: Int): Boolean {
        val root = _root ?: return false
        if (root.leafCount() >= MAX_PANES) return false

        val newRoot = splitNode(root, _focusedSessionIndex, direction, newSessionIndex)
        if (newRoot === root) return false // Focused pane not found (shouldn't happen)

        _root = newRoot
        // Keep focus on the original pane (don't switch to the new one)
        return true
    }

    /**
     * Close a pane by session index. Collapses the tree by replacing the parent
     * split with the sibling node.
     *
     * @param sessionIndex The session to remove from the pane tree.
     * @return The list of remaining session indices, or null if only one pane was left
     *         (meaning the tab should revert to single-pane mode).
     */
    fun closePane(sessionIndex: Int): List<Int>? {
        val root = _root ?: return null

        // If it's just a leaf, closing it means closing the tab entirely
        if (root is PaneNode.Leaf) {
            _root = null
            return null
        }

        val newRoot = removeNode(root, sessionIndex)
        _root = newRoot

        // If focus was on the closed pane, move focus to the first remaining leaf
        if (_focusedSessionIndex == sessionIndex) {
            _focusedSessionIndex = newRoot?.allSessionIndices()?.firstOrNull() ?: -1
        }

        return newRoot?.allSessionIndices()
    }

    /**
     * Update session indices after a session is removed from the global list.
     * All indices >= removedIndex get decremented by 1.
     */
    fun adjustIndicesAfterRemoval(removedIndex: Int) {
        _root = _root?.let { adjustNode(it, removedIndex) }
        if (_focusedSessionIndex > removedIndex) {
            _focusedSessionIndex--
        } else if (_focusedSessionIndex == removedIndex) {
            _focusedSessionIndex = _root?.allSessionIndices()?.firstOrNull() ?: -1
        }
    }

    // ── Tree manipulation (immutable transforms) ─────────────

    /** Recursively find the focused leaf and replace it with a split. */
    private fun splitNode(
        node: PaneNode,
        targetSessionIndex: Int,
        direction: SplitDirection,
        newSessionIndex: Int,
    ): PaneNode = when (node) {
        is PaneNode.Leaf -> {
            if (node.sessionIndex == targetSessionIndex) {
                PaneNode.Split(
                    direction = direction,
                    ratio = 0.5f,
                    first = node,
                    second = PaneNode.Leaf(newSessionIndex),
                )
            } else {
                node // Not the target, return unchanged
            }
        }
        is PaneNode.Split -> {
            val newFirst = splitNode(node.first, targetSessionIndex, direction, newSessionIndex)
            if (newFirst !== node.first) {
                node.copy(first = newFirst)
            } else {
                val newSecond = splitNode(node.second, targetSessionIndex, direction, newSessionIndex)
                if (newSecond !== node.second) {
                    node.copy(second = newSecond)
                } else {
                    node // Target not found in either subtree
                }
            }
        }
    }

    /** Recursively remove a leaf and collapse its parent split. */
    private fun removeNode(node: PaneNode, targetSessionIndex: Int): PaneNode? = when (node) {
        is PaneNode.Leaf -> {
            if (node.sessionIndex == targetSessionIndex) null else node
        }
        is PaneNode.Split -> {
            val newFirst = removeNode(node.first, targetSessionIndex)
            val newSecond = removeNode(node.second, targetSessionIndex)
            when {
                newFirst == null && newSecond == null -> null
                newFirst == null -> newSecond
                newSecond == null -> newFirst
                else -> node.copy(first = newFirst, second = newSecond)
            }
        }
    }

    /** Adjust session indices after a global session removal. */
    private fun adjustNode(node: PaneNode, removedIndex: Int): PaneNode = when (node) {
        is PaneNode.Leaf -> {
            if (node.sessionIndex > removedIndex) {
                node.copy(sessionIndex = node.sessionIndex - 1)
            } else {
                node
            }
        }
        is PaneNode.Split -> {
            node.copy(
                first = adjustNode(node.first, removedIndex),
                second = adjustNode(node.second, removedIndex),
            )
        }
    }
}
