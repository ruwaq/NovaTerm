package com.novaterm.core.session.engine

import android.util.Log
import com.novaterm.core.common.contract.TerminalEngine
import com.novaterm.core.common.model.CursorPosition
import com.novaterm.core.common.model.TerminalDimensions
import java.util.concurrent.atomic.AtomicLong

/**
 * Full Rust terminal session: PTY + VT parser + I/O threads.
 *
 * Unlike [RustEngine] (which only handles VT parsing, with PTY in Java),
 * this engine manages EVERYTHING in Rust via [NativeSession]:
 * - PTY fork/exec (replaces termux.c)
 * - Reader/writer daemon threads
 * - VT parsing (alacritty_terminal)
 * - Grid state
 *
 * Usage:
 * 1. Create via [spawn]
 * 2. Call [processPending] periodically (e.g., on screen update timer)
 * 3. Read [getGrid]/[getCursor] for rendering
 * 4. Call [write] for user keyboard input
 * 5. Call [destroy] when session ends
 */
class RustSessionEngine private constructor(
    handle: Long,
    private var rows: Int,
    private var cols: Int,
    /** Child process PID. */
    val pid: Int,
) : TerminalEngine {

    private val handleRef = AtomicLong(handle)

    private fun validHandle(): Long {
        val h = handleRef.get()
        return if (h > 0) h else -1
    }

    /**
     * Expose the raw native handle for the GPU renderer.
     * Package-internal: only [GpuRenderer] should call this.
     */
    internal fun nativeHandle(): Long = validHandle()

    /**
     * Process pending PTY output through the VT parser.
     * Call this before reading grid/cursor to get latest state.
     * Returns the number of bytes processed.
     */
    fun processPending(): Int {
        val h = validHandle()
        if (h < 0) return 0
        return NativeSession.nativeProcessPending(h)
    }

    override fun processBytes(data: ByteArray) {
        // In unified mode, PTY bytes are buffered by the Rust reader thread.
        // This method triggers processing of that buffer.
        processPending()
    }

    /** Write user input to the PTY (sent to the shell). */
    fun write(data: ByteArray) {
        val h = validHandle()
        if (h < 0) return
        NativeSession.nativeWrite(h, data)
    }

    override fun getGrid(): IntArray? {
        val h = validHandle()
        if (h < 0) return null
        return NativeSession.nativeGetGrid(h)
    }

    override fun getCursor(): CursorPosition {
        val h = validHandle()
        if (h < 0) return CursorPosition(0, 0)
        val c = NativeSession.nativeGetCursor(h) ?: return CursorPosition(0, 0)
        if (c.size < 2) return CursorPosition(0, 0)
        return CursorPosition(row = c[0], column = c[1])
    }

    override fun getTitle(): String = "" // Title delivered via events

    override fun resize(dimensions: TerminalDimensions) {
        val h = validHandle()
        if (h < 0) return
        rows = dimensions.rows
        cols = dimensions.columns
        NativeSession.nativeResize(h, rows, cols)
    }

    override fun scroll(delta: Int) {
        // Scrollback handled by Rust backend internally
    }

    override fun reset() {
        // Reset not applicable for unified session — destroy and recreate
    }

    override fun getDimensions(): TerminalDimensions {
        return TerminalDimensions(rows = rows, columns = cols)
    }

    override fun hasPendingEvents(): Boolean {
        val h = validHandle()
        if (h < 0) return false
        return NativeSession.nativeHasPending(h)
    }

    override fun drainPtyWrites(): ByteArray? = null // Handled internally by Rust

    override fun destroy() {
        val h = handleRef.getAndSet(-1)
        if (h <= 0) return
        NativeSession.nativeDestroy(h)
        Log.d(TAG, "RustSessionEngine destroyed (remaining: ${NativeSession.nativeSessionCount()})")
    }

    companion object {
        private const val TAG = "RustSessionEngine"

        /**
         * Spawn a complete Rust terminal session.
         *
         * @param shell executable (e.g., /system/bin/linker64)
         * @param cwd working directory
         * @param args command arguments
         * @param envVars environment as "KEY=VALUE" strings
         * @param rows initial terminal rows
         * @param cols initial terminal columns
         * @return RustSessionEngine or null on failure
         */
        fun spawn(
            shell: String,
            cwd: String,
            args: Array<String>,
            envVars: Array<String>,
            rows: Int = 24,
            cols: Int = 80,
        ): RustSessionEngine? {
            NativeTerminal.ensureLoaded()
            val handle = NativeSession.nativeSpawn(shell, cwd, args, envVars, rows, cols)
            if (handle <= 0) {
                Log.e(TAG, "Failed to spawn Rust session")
                return null
            }
            val pid = NativeSession.nativeGetPid(handle)
            Log.i(TAG, "RustSessionEngine spawned: pid=$pid ${rows}x${cols}")
            return RustSessionEngine(handle, rows, cols, pid)
        }
    }
}
