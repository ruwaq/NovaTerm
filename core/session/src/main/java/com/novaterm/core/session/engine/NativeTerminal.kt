package com.novaterm.core.session.engine

/**
 * JNI bridge to the Rust terminal core (libnovaterm.so).
 *
 * This class declares the native methods that match the JNI exports
 * in rust-core/novaterm-bridge/src/jni_bridge.rs. The Rust side uses
 * alacritty_terminal 0.25.1 for VT parsing and grid management.
 *
 * Thread safety: all native methods are safe to call from any thread.
 * The Rust side uses parking_lot::Mutex for synchronization.
 */
internal object NativeTerminal {

    private var initialized = false

    /**
     * Load the native library and initialize the Rust logger.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun ensureLoaded() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            System.loadLibrary("novaterm")
            nativeInit()
            initialized = true
        }
    }

    /** Initialize Rust logger → Android logcat. */
    private external fun nativeInit()

    /**
     * Create a new terminal backend with the given dimensions.
     * @return positive handle on success, -1 on error.
     */
    external fun nativeCreate(rows: Int, cols: Int): Long

    /** Destroy a terminal backend and free resources. */
    external fun nativeDestroy(handle: Long)

    /**
     * Feed raw PTY output bytes into the VT parser.
     * This is the HOT PATH — called for every chunk of PTY data.
     */
    external fun nativeProcessBytes(handle: Long, data: ByteArray)

    /** Resize the terminal grid. */
    external fun nativeResize(handle: Long, rows: Int, cols: Int)

    /**
     * Get the visible grid as a flat int array.
     *
     * Returns int[rows*cols*4] where each cell is:
     *   [character_codepoint, fg_argb, bg_argb, flags]
     *
     * @return null if the handle is invalid.
     */
    external fun nativeGetGrid(handle: Long): IntArray?

    /**
     * Get cursor state.
     * @return int[4] = [row, col, shape, visible] or null.
     */
    external fun nativeGetCursor(handle: Long): IntArray?

    /** Get the terminal title (set via OSC 0/2). */
    external fun nativeGetTitle(handle: Long): String?

    /** Scroll the terminal viewport. */
    external fun nativeScroll(handle: Long, delta: Int)

    /** Reset terminal to initial state. */
    external fun nativeReset(handle: Long)

    /** Check if there are pending events. */
    external fun nativeHasEvents(handle: Long): Boolean

    /** Get count of active backends (for debugging). */
    external fun nativeActiveCount(): Int
}
