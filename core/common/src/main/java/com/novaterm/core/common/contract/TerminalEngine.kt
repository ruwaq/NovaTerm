package com.novaterm.core.common.contract

import com.novaterm.core.common.model.CursorPosition
import com.novaterm.core.common.model.TerminalDimensions

/**
 * Strangler Fig interface for terminal emulation backends.
 *
 * Phase 1 (current): LegacyEngine wraps Java TerminalEmulator
 * Phase 2 (migration): RustEngine wraps Rust alacritty_terminal via JNI
 *
 * The SessionManager owns PTY lifecycle. The engine only handles
 * VT parsing and grid state — it receives bytes from PTY output
 * and produces a renderable grid.
 */
interface TerminalEngine {

    /** Feed raw bytes from PTY output into the VT parser. */
    fun processBytes(data: ByteArray)

    /**
     * Get the visible grid as a flat IntArray.
     *
     * Layout: [char, fg_argb, bg_argb, flags] per cell, row-major order.
     * Total size = rows * cols * 4.
     * ARGB format: 0xAARRGGBB (alpha in high byte, blue in low byte).
     *
     * Flag bits: 0=bold, 1=italic, 2=underline, 3=strikethrough,
     * 4=inverse, 5=hidden, 6=dim, 7=double_underline, 8=undercurl,
     * 9=wide_char.
     */
    fun getGrid(): IntArray?

    /** Get current cursor state. */
    fun getCursor(): CursorPosition

    /** Get terminal title (set via OSC 0/2). */
    fun getTitle(): String

    /** Resize the terminal grid. */
    fun resize(dimensions: TerminalDimensions)

    /** Scroll the viewport (positive = up, negative = down). */
    fun scroll(delta: Int)

    /** Reset terminal to initial state. */
    fun reset()

    /** Get current grid dimensions. */
    fun getDimensions(): TerminalDimensions

    /** Check if there are pending events (bell, title, clipboard). */
    fun hasPendingEvents(): Boolean

    /**
     * Drain bytes that the terminal needs to write back to the PTY.
     * These are responses to DA (Device Attributes), DSR (Device Status Report),
     * and other query sequences. Must be written to the PTY fd by the caller.
     * @return bytes to write, or null if none pending.
     */
    fun drainPtyWrites(): ByteArray?

    /** Release native resources. Must be called when the session ends. */
    fun destroy()
}

/**
 * Marker interface for engines backed by a Rust native handle.
 * Required for GPU rendering which passes the handle to the Vulkan renderer.
 */
interface NativeEngine {
    /** The Rust native handle for this engine instance. */
    fun nativeHandle(): Long
}

/** Events emitted by the terminal engine. */
sealed interface EngineEvent {
    data object Bell : EngineEvent
    data class TitleChanged(val title: String) : EngineEvent
    data class ClipboardStore(val text: String) : EngineEvent
    data object ClipboardLoad : EngineEvent
    data class ChildExit(val code: Int) : EngineEvent
}

/** Factory for creating terminal engine instances. */
interface TerminalEngineFactory {
    fun create(dimensions: TerminalDimensions): TerminalEngine
}
