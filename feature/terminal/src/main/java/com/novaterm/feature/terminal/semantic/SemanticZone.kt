package com.novaterm.feature.terminal.semantic

/**
 * Represents a semantic zone in terminal output, as defined by OSC 133.
 *
 * OSC 133 marks regions of terminal output with semantic meaning:
 * - PROMPT: the shell prompt text (e.g., "user@host:~$ ")
 * - INPUT: the command typed by the user
 * - OUTPUT: the command's output
 *
 * This enables:
 * - Jump between prompts (navigate command history visually)
 * - Select only the output of a specific command
 * - Distinguish AI-generated commands from user-typed ones
 * - Structured block creation for the BlockStore
 *
 * OSC 133 format:
 *   ESC ] 133 ; A ST  — Fresh prompt (new command cycle starts)
 *   ESC ] 133 ; B ST  — Command started (user pressed Enter)
 *   ESC ] 133 ; C ST  — Command output begins
 *   ESC ] 133 ; D ; exitcode ST  — Command finished with exit code
 */
enum class ZoneType {
    PROMPT,   // A: shell prompt area
    INPUT,    // B: user command input
    OUTPUT,   // C: command output
}

data class SemanticZone(
    val type: ZoneType,
    val startRow: Int,
    val startCol: Int,
    val endRow: Int = -1,    // -1 = zone still open
    val endCol: Int = -1,
    val exitCode: Int? = null, // only set when D marker received
    val command: String? = null,
)

/**
 * Tracks semantic zones as OSC 133 markers are received.
 * Maintains a list of zones for the current terminal session.
 *
 * Usage: call [onOsc133] when the terminal emulator receives an OSC 133 sequence.
 * The tracker maintains state and emits complete blocks when a command cycle finishes.
 */
class SemanticZoneTracker {

    /** Maximum zones to keep in memory to prevent unbounded growth. */
    private val maxZones = 1000

    private val _zones = mutableListOf<SemanticZone>()
    val zones: List<SemanticZone> get() = _zones

    /** Trim oldest zones when we exceed maxZones to prevent memory leak. */
    private fun trimIfNeeded() {
        if (_zones.size > maxZones) {
            // Remove oldest 20% to avoid frequent trimming
            val removeCount = maxZones / 5
            repeat(removeCount) { _zones.removeFirstOrNull() }
        }
    }

    private var currentZone: SemanticZone? = null
    private var currentCommand: String? = null

    /** Callback when a complete command block is detected (prompt → input → output → done). */
    var onBlockComplete: ((command: String, exitCode: Int?) -> Unit)? = null

    /**
     * Process an OSC 133 marker from the terminal emulator.
     *
     * @param marker The marker character: 'A', 'B', 'C', or 'D'
     * @param params Optional parameters (e.g., exit code for 'D')
     * @param row Current cursor row in the terminal buffer
     * @param col Current cursor column
     */
    fun onOsc133(marker: Char, params: String?, row: Int, col: Int) {
        when (marker) {
            'A' -> {
                // Fresh prompt — new command cycle
                closeCurrentZone(row, col)
                val zone = SemanticZone(ZoneType.PROMPT, startRow = row, startCol = col)
                currentZone = zone
                _zones.add(zone)
                trimIfNeeded()
                currentCommand = null
            }
            'B' -> {
                // Command started (user pressed Enter)
                closeCurrentZone(row, col)
                val zone = SemanticZone(ZoneType.INPUT, startRow = row, startCol = col)
                currentZone = zone
                _zones.add(zone)
                trimIfNeeded()
            }
            'C' -> {
                // Command output begins
                closeCurrentZone(row, col)
                val zone = SemanticZone(ZoneType.OUTPUT, startRow = row, startCol = col)
                currentZone = zone
                _zones.add(zone)
                trimIfNeeded()
            }
            'D' -> {
                // Command finished with exit code
                val exitCode = params?.toIntOrNull()
                closeCurrentZone(row, col)

                // Emit complete block
                val cmd = currentCommand
                if (cmd != null) {
                    onBlockComplete?.invoke(cmd, exitCode)
                }
                currentCommand = null
            }
        }
    }

    /**
     * Set the command text for the current input zone.
     * Called when the terminal detects the command text between B and C markers.
     */
    fun setCurrentCommand(command: String) {
        currentCommand = command
    }

    /**
     * Find the zone at a given terminal row.
     * Useful for "jump to previous/next prompt" navigation.
     */
    fun findZoneAt(row: Int): SemanticZone? {
        return _zones.lastOrNull { zone ->
            row >= zone.startRow && (zone.endRow == -1 || row <= zone.endRow)
        }
    }

    /**
     * Get all prompt positions for jump-to-prompt navigation.
     */
    fun getPromptPositions(): List<Int> {
        return _zones.filter { it.type == ZoneType.PROMPT }.map { it.startRow }
    }

    /**
     * Clear all zones (e.g., on terminal reset).
     */
    fun clear() {
        _zones.clear()
        currentZone = null
        currentCommand = null
    }

    private fun closeCurrentZone(row: Int, col: Int) {
        val zone = currentZone ?: return
        val index = _zones.indexOf(zone)
        if (index >= 0) {
            _zones[index] = zone.copy(endRow = row, endCol = col)
        }
        currentZone = null
    }
}
