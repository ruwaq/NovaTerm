package com.novaterm.core.session.engine

import com.novaterm.core.common.contract.TerminalEngine
import com.novaterm.core.common.contract.TerminalEngineFactory
import com.novaterm.core.common.model.CursorPosition
import com.novaterm.core.common.model.TerminalDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the TerminalEngine contract using a mock implementation.
 * Verifies that the interface is correct and usable without JNI.
 */
class TerminalEngineContractTest {

    /** Simple in-memory engine for testing the contract. */
    private class FakeEngine(private var dims: TerminalDimensions) : TerminalEngine {
        private var bytesProcessed = 0
        private var destroyed = false
        private var cursorRow = 0
        private var cursorCol = 0
        private var title = "fake"

        override fun processBytes(data: ByteArray) {
            bytesProcessed += data.size
        }

        override fun getGrid(): IntArray {
            val size = dims.rows * dims.columns * 4
            return IntArray(size) { 0 }
        }

        override fun getCursor(): CursorPosition = CursorPosition(cursorRow, cursorCol)

        override fun getTitle(): String = title

        override fun resize(dimensions: TerminalDimensions) {
            dims = dimensions
        }

        override fun scroll(delta: Int) {}

        override fun reset() {
            bytesProcessed = 0
            cursorRow = 0
            cursorCol = 0
        }

        override fun getDimensions(): TerminalDimensions = dims

        override fun hasPendingEvents(): Boolean = false

        override fun destroy() {
            destroyed = true
        }

        fun isDestroyed() = destroyed
        fun totalBytesProcessed() = bytesProcessed
    }

    private class FakeEngineFactory : TerminalEngineFactory {
        val engines = mutableListOf<FakeEngine>()

        override fun create(dimensions: TerminalDimensions): TerminalEngine {
            val engine = FakeEngine(dimensions)
            engines.add(engine)
            return engine
        }
    }

    @Test
    fun `factory creates engine with correct dimensions`() {
        val factory = FakeEngineFactory()
        val dims = TerminalDimensions(rows = 24, columns = 80)
        val engine = factory.create(dims)

        assertEquals(dims, engine.getDimensions())
    }

    @Test
    fun `processBytes accumulates data`() {
        val engine = FakeEngine(TerminalDimensions(24, 80))
        engine.processBytes("Hello".toByteArray())
        engine.processBytes(" World".toByteArray())

        assertEquals(11, engine.totalBytesProcessed())
    }

    @Test
    fun `getGrid returns correctly sized array`() {
        val engine = FakeEngine(TerminalDimensions(24, 80))
        val grid = engine.getGrid()

        assertNotNull(grid)
        assertEquals(24 * 80 * 4, grid!!.size)
    }

    @Test
    fun `resize updates dimensions`() {
        val engine = FakeEngine(TerminalDimensions(24, 80))
        engine.resize(TerminalDimensions(48, 120))

        assertEquals(TerminalDimensions(48, 120), engine.getDimensions())
    }

    @Test
    fun `reset clears state`() {
        val engine = FakeEngine(TerminalDimensions(24, 80))
        engine.processBytes("test".toByteArray())
        engine.reset()

        assertEquals(0, engine.totalBytesProcessed())
    }

    @Test
    fun `destroy marks engine as destroyed`() {
        val engine = FakeEngine(TerminalDimensions(24, 80))
        engine.destroy()

        assertTrue(engine.isDestroyed())
    }

    @Test
    fun `cursor starts at origin`() {
        val engine = FakeEngine(TerminalDimensions(24, 80))
        val cursor = engine.getCursor()

        assertEquals(0, cursor.row)
        assertEquals(0, cursor.column)
    }

    @Test
    fun `factory tracks created engines`() {
        val factory = FakeEngineFactory()
        factory.create(TerminalDimensions(24, 80))
        factory.create(TerminalDimensions(48, 120))

        assertEquals(2, factory.engines.size)
    }
}
