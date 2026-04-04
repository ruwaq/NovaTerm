package com.novaterm.core.session.engine

import android.util.Log
import com.novaterm.core.common.contract.TerminalEngine
import com.novaterm.core.common.contract.TerminalEngineFactory
import com.novaterm.core.common.model.CursorPosition
import com.novaterm.core.common.model.TerminalDimensions
import java.util.concurrent.atomic.AtomicLong

/**
 * Terminal engine backed by the Rust core (alacritty_terminal via JNI).
 *
 * Thread safety: [handle] uses AtomicLong with compareAndSet for
 * safe concurrent access. destroy() atomically invalidates the handle
 * before calling native destroy, preventing use-after-free.
 */
class RustEngine private constructor(
    handle: Long,
    @Volatile private var rows: Int,
    @Volatile private var cols: Int,
) : TerminalEngine {

    private val handleRef = AtomicLong(handle)

    private fun validHandle(): Long {
        val h = handleRef.get()
        return if (h > 0) h else -1
    }

    override fun processBytes(data: ByteArray) {
        val h = validHandle()
        if (h < 0) return
        NativeTerminal.nativeProcessBytes(h, data)
    }

    @Synchronized
    override fun getGrid(): IntArray? {
        val h = validHandle()
        if (h < 0) return null
        return NativeTerminal.nativeGetGrid(h)
    }

    @Synchronized
    override fun getCursor(): CursorPosition {
        val h = validHandle()
        if (h < 0) return CursorPosition(0, 0)
        val cursor = NativeTerminal.nativeGetCursor(h)
        if (cursor == null || cursor.size < 2) return CursorPosition(0, 0)
        return CursorPosition(row = cursor[0], column = cursor[1])
    }

    override fun getTitle(): String {
        val h = validHandle()
        if (h < 0) return ""
        return NativeTerminal.nativeGetTitle(h) ?: ""
    }

    override fun resize(dimensions: TerminalDimensions) {
        val h = validHandle()
        if (h < 0) return
        rows = dimensions.rows
        cols = dimensions.columns
        NativeTerminal.nativeResize(h, dimensions.rows, dimensions.columns)
    }

    override fun scroll(delta: Int) {
        val h = validHandle()
        if (h < 0) return
        NativeTerminal.nativeScroll(h, delta)
    }

    override fun reset() {
        val h = validHandle()
        if (h < 0) return
        NativeTerminal.nativeReset(h)
    }

    @Synchronized
    override fun getDimensions(): TerminalDimensions {
        return TerminalDimensions(rows = rows, columns = cols)
    }

    override fun hasPendingEvents(): Boolean {
        val h = validHandle()
        if (h < 0) return false
        return NativeTerminal.nativeHasEvents(h)
    }

    override fun drainPtyWrites(): ByteArray? {
        val h = validHandle()
        if (h < 0) return null
        return NativeTerminal.nativeDrainPtyWrites(h)
    }

    /**
     * Atomically invalidate the handle, then destroy the native resource.
     * compareAndSet ensures only one thread performs the destroy.
     */
    @Synchronized
    override fun destroy() {
        val h = handleRef.getAndSet(-1)
        if (h <= 0) return // Already destroyed or invalid
        NativeTerminal.nativeDestroy(h)
        Log.d(TAG, "RustEngine destroyed (remaining: ${NativeTerminal.nativeActiveCount()})")
    }

    companion object {
        private const val TAG = "RustEngine"
    }

    class Factory : TerminalEngineFactory {
        init {
            NativeTerminal.ensureLoaded()
        }

        override fun create(dimensions: TerminalDimensions): TerminalEngine {
            require(dimensions.rows > 0 && dimensions.columns > 0) {
                "Dimensions must be positive: ${dimensions.rows}x${dimensions.columns}"
            }
            val handle = NativeTerminal.nativeCreate(dimensions.rows, dimensions.columns)
            if (handle <= 0) {
                throw IllegalStateException("Failed to create Rust terminal backend")
            }
            Log.d(TAG, "RustEngine created: ${dimensions.rows}x${dimensions.columns} (handle=$handle)")
            return RustEngine(handle, dimensions.rows, dimensions.columns)
        }
    }
}
