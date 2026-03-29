package com.novaterm.core.session.engine

/**
 * JNI bridge to the unified Rust session (PTY + VT + I/O threads).
 *
 * This replaces BOTH JNI.java (termux.c PTY) and NativeTerminal (VT parser).
 * One handle = one complete terminal session managed entirely in Rust.
 *
 * Lifecycle:
 *   nativeSpawn() → handle
 *   nativeProcessPending() (called on each screen update)
 *   nativeGetGrid() / nativeGetCursor() (for rendering)
 *   nativeWrite() (user keyboard input)
 *   nativeResize() (terminal view resize)
 *   nativeDestroy() (session end)
 */
internal object NativeSession {

    init {
        NativeTerminal.ensureLoaded() // Same .so contains both APIs
    }

    /**
     * Spawn a complete Rust session: PTY + VT parser + I/O threads.
     *
     * @param shell executable (e.g., /system/bin/linker64 for W^X bypass)
     * @param cwd working directory
     * @param args command arguments (String[])
     * @param envVars environment as "KEY=VALUE" strings (String[])
     * @param rows initial terminal rows
     * @param cols initial terminal columns
     * @return session handle (>0) or -1 on error
     */
    external fun nativeSpawn(
        shell: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>,
        rows: Int,
        cols: Int,
    ): Long

    /** Process pending PTY output through VT parser. Returns bytes processed. */
    external fun nativeProcessPending(handle: Long): Int

    /** Write user input to the PTY. */
    external fun nativeWrite(handle: Long, data: ByteArray)

    /** Get visible grid as int[rows*cols*4]. */
    external fun nativeGetGrid(handle: Long): IntArray?

    /** Get cursor as int[4]: [row, col, shape, visible]. */
    external fun nativeGetCursor(handle: Long): IntArray?

    /** Resize PTY + VT parser. */
    external fun nativeResize(handle: Long, rows: Int, cols: Int)

    /** Get child process PID. */
    external fun nativeGetPid(handle: Long): Int

    /** Stop and destroy the session. */
    external fun nativeDestroy(handle: Long)

    /** Check if there's pending PTY output to process. */
    external fun nativeHasPending(handle: Long): Boolean

    /** Get number of active sessions (debug). */
    external fun nativeSessionCount(): Int
}
