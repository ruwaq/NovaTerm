package com.novaterm.core.session.engine

import com.novaterm.core.common.contract.TerminalEngine
import com.novaterm.core.common.contract.TerminalEngineFactory
import com.novaterm.core.common.model.CursorPosition
import com.novaterm.core.common.model.TerminalDimensions
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle

/**
 * Terminal engine wrapping the existing Java TerminalEmulator (Termux).
 *
 * Phase 1 implementation. The existing TerminalView reads from
 * TerminalEmulator directly for rendering. This wrapper provides the
 * TerminalEngine interface for API compatibility and future migration.
 *
 * In the legacy path, PTY bytes flow through TerminalSession's internal
 * threads, so processBytes() is a no-op here.
 */
class LegacyTermuxEngine(
    private val session: TerminalSession,
) : TerminalEngine {

    private val emulator: TerminalEmulator?
        get() = session.emulator

    override fun processBytes(data: ByteArray) {
        // No-op: TerminalSession's background thread feeds bytes
        // to TerminalEmulator.append() via MainThreadHandler.
    }

    override fun getGrid(): IntArray? {
        val emu = emulator ?: return null
        val screen = emu.screen
        val rows = emu.mRows
        val cols = emu.mColumns
        val grid = IntArray(rows * cols * 4)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val idx = (row * cols + col) * 4
                val style = screen.getStyleAt(row, col)
                val foreColor = TextStyle.decodeForeColor(style)
                val backColor = TextStyle.decodeBackColor(style)
                val effect = TextStyle.decodeEffect(style)

                // Character: use screen's internal text access
                // TerminalBuffer doesn't expose per-cell char directly,
                // so we extract from the selected text area for the cell
                grid[idx] = ' '.code // Character access requires row object
                grid[idx + 1] = colorToArgb(foreColor)
                grid[idx + 2] = colorToArgb(backColor)
                grid[idx + 3] = effectToFlags(effect)
            }
        }
        return grid
    }

    override fun getCursor(): CursorPosition {
        val emu = emulator ?: return CursorPosition(0, 0)
        return CursorPosition(row = emu.cursorRow, column = emu.cursorCol)
    }

    override fun getTitle(): String {
        return session.title ?: "shell"
    }

    override fun resize(dimensions: TerminalDimensions) {
        session.updateSize(dimensions.columns, dimensions.rows, 0, 0)
    }

    override fun scroll(delta: Int) {
        // Scrolling is handled by TerminalView in the legacy path
    }

    override fun reset() {
        emulator?.reset()
    }

    override fun getDimensions(): TerminalDimensions {
        val emu = emulator ?: return TerminalDimensions(24, 80)
        return TerminalDimensions(rows = emu.mRows, columns = emu.mColumns)
    }

    override fun hasPendingEvents(): Boolean = false

    override fun drainPtyWrites(): ByteArray? = null

    override fun destroy() {
        // Session lifecycle managed by TermuxSessionManager
    }

    companion object {
        private fun effectToFlags(effect: Int): Int {
            var flags = 0
            if (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD != 0) flags = flags or (1 shl 0)
            if (effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC != 0) flags = flags or (1 shl 1)
            if (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE != 0) flags = flags or (1 shl 2)
            if (effect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH != 0) flags = flags or (1 shl 3)
            if (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0) flags = flags or (1 shl 4)
            if (effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE != 0) flags = flags or (1 shl 5)
            if (effect and TextStyle.CHARACTER_ATTRIBUTE_DIM != 0) flags = flags or (1 shl 6)
            return flags
        }

        /** Convert Termux color index or truecolor to packed ARGB. */
        private fun colorToArgb(color: Int): Int {
            // Truecolor: already has 0xFF in high byte
            if (color and 0xFF000000.toInt() == 0xFF000000.toInt()) {
                return color
            }
            // Indexed color: map to Gruvbox Dark palette
            return GRUVBOX_PALETTE.getOrElse(color) { 0xFFEBDBB2.toInt() }
        }

        private val GRUVBOX_PALETTE = intArrayOf(
            0xFF282828.toInt(), // 0 Black
            0xFFCC241D.toInt(), // 1 Red
            0xFF98971A.toInt(), // 2 Green
            0xFFD79921.toInt(), // 3 Yellow
            0xFF458588.toInt(), // 4 Blue
            0xFFB16286.toInt(), // 5 Magenta
            0xFF689D6A.toInt(), // 6 Cyan
            0xFFA89984.toInt(), // 7 White
            0xFF928374.toInt(), // 8 Bright Black
            0xFFFB4934.toInt(), // 9 Bright Red
            0xFFB8BB26.toInt(), // 10 Bright Green
            0xFFFABD2F.toInt(), // 11 Bright Yellow
            0xFF83A598.toInt(), // 12 Bright Blue
            0xFFD3869B.toInt(), // 13 Bright Magenta
            0xFF8EC07C.toInt(), // 14 Bright Cyan
            0xFFEBDBB2.toInt(), // 15 Bright White
        )
    }
}
