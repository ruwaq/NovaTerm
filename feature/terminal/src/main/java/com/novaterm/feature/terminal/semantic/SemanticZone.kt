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

    // All access to _zones is synchronized via this lock.
    // onOsc133 is called from the terminal I/O thread; getPromptPositions/findZoneAt from UI.
    private val lock = Any()

    private val _zones = mutableListOf<SemanticZone>()
    val zones: List<SemanticZone> get() = synchronized(lock) { _zones.toList() }

    /** Trim oldest zones when we exceed maxZones to prevent memory leak. */
    private fun trimIfNeeded() {
        if (_zones.size > maxZones) {
            val removeCount = maxZones / 5
            repeat(removeCount) { _zones.removeFirstOrNull() }
        }
    }

    private var currentZone: SemanticZone? = null
    private var currentCommand: String? = null
    private var commandStartTimeMs: Long = 0L

    /** Callback when a complete command block is detected (prompt → input → output → done). */
    @Volatile var onBlockComplete: ((command: String, exitCode: Int?) -> Unit)? = null

    /** Enhanced callback with command duration (C marker → D marker). */
    @Volatile var onBlockCompleteWithDuration: ((command: String, exitCode: Int?, durationMs: Long) -> Unit)? = null

    fun onOsc133(marker: Char, params: String?, row: Int, col: Int) {
        // Callbacks are invoked outside the lock to avoid holding it during user code.
        var blockCmd: String? = null
        var blockExitCode: Int? = null
        var blockDurationMs = 0L

        synchronized(lock) {
            when (marker) {
                'A' -> {
                    closeCurrentZone(row, col)
                    val zone = SemanticZone(ZoneType.PROMPT, startRow = row, startCol = col)
                    currentZone = zone
                    _zones.add(zone)
                    trimIfNeeded()
                    currentCommand = null
                }
                'B' -> {
                    closeCurrentZone(row, col)
                    val zone = SemanticZone(ZoneType.INPUT, startRow = row, startCol = col)
                    currentZone = zone
                    _zones.add(zone)
                    trimIfNeeded()
                }
                'C' -> {
                    closeCurrentZone(row, col)
                    val zone = SemanticZone(ZoneType.OUTPUT, startRow = row, startCol = col)
                    currentZone = zone
                    _zones.add(zone)
                    trimIfNeeded()
                    commandStartTimeMs = System.currentTimeMillis()
                }
                'D' -> {
                    blockExitCode = params?.toIntOrNull()
                    closeCurrentZone(row, col)
                    blockDurationMs = if (commandStartTimeMs > 0L) {
                        System.currentTimeMillis() - commandStartTimeMs
                    } else { 0L }
                    commandStartTimeMs = 0L
                    blockCmd = currentCommand
                    currentCommand = null
                }
            }
        }

        // Invoke callbacks outside lock
        if (blockCmd != null) {
            onBlockComplete?.invoke(blockCmd!!, blockExitCode)
            onBlockCompleteWithDuration?.invoke(blockCmd!!, blockExitCode, blockDurationMs)
        }
    }

    fun setCurrentCommand(command: String) {
        synchronized(lock) { currentCommand = command }
    }

    fun findZoneAt(row: Int): SemanticZone? {
        synchronized(lock) {
            return _zones.lastOrNull { zone ->
                row >= zone.startRow && (zone.endRow == -1 || row <= zone.endRow)
            }
        }
    }

    fun getPromptPositions(): List<Int> {
        synchronized(lock) {
            return _zones.filter { it.type == ZoneType.PROMPT }.map { it.startRow }
        }
    }

    fun clear() {
        synchronized(lock) {
            _zones.clear()
            currentZone = null
            currentCommand = null
        }
    }

    private fun closeCurrentZone(row: Int, col: Int) {
        val zone = currentZone ?: return
        // Current zone is always the last added element — O(1) instead of indexOf O(n)
        val lastIdx = _zones.lastIndex
        if (lastIdx >= 0 && _zones[lastIdx] === zone) {
            _zones[lastIdx] = zone.copy(endRow = row, endCol = col)
        }
        currentZone = null
    }
}
