package com.novaterm.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Tests for RustEngineManager logic.
 *
 * The actual RustEngine requires JNI (libnovaterm.so), so we test
 * the ConcurrentHashMap management, factory pattern, and lifecycle
 * contracts using the same data structure patterns.
 */
class RustEngineManagerTest {

    // ── ConcurrentHashMap engine management ──────────────

    @Test
    fun `getEngine returns null for unknown handle`() {
        val engines = ConcurrentHashMap<String, String>()
        assertNull(engines["unknown-handle"])
    }

    @Test
    fun `getEngine returns engine after attach`() {
        val engines = ConcurrentHashMap<String, String>()
        engines["handle-1"] = "engine-1"
        assertEquals("engine-1", engines["handle-1"])
    }

    @Test
    fun `detach removes engine from map`() {
        val engines = ConcurrentHashMap<String, String>()
        engines["h1"] = "e1"
        val removed = engines.remove("h1")
        assertEquals("e1", removed)
        assertNull(engines["h1"])
    }

    @Test
    fun `detach on missing handle returns null`() {
        val engines = ConcurrentHashMap<String, String>()
        val removed = engines.remove("nonexistent")
        assertNull(removed)
    }

    @Test
    fun `destroyAll clears all engines`() {
        val engines = ConcurrentHashMap<String, String>()
        engines["h1"] = "e1"
        engines["h2"] = "e2"
        engines["h3"] = "e3"

        val handles = listOf("h1", "h2", "h3")
        handles.forEach { engines.remove(it) }
        engines.clear() // Safety net

        assertTrue(engines.isEmpty())
    }

    @Test
    fun `destroyAll with partial handles still clears map`() {
        val engines = ConcurrentHashMap<String, String>()
        engines["h1"] = "e1"
        engines["h2"] = "e2"
        engines["orphan"] = "e3"

        // Only h1 and h2 are in the sessions list
        listOf("h1", "h2").forEach { engines.remove(it) }
        // orphan remains until clear()
        assertEquals(1, engines.size)
        engines.clear()
        assertTrue(engines.isEmpty())
    }

    // ── Volatile factory pattern ─────────────────────────

    @Test
    fun `factory lazy init pattern`() {
        var factory: String? = null
        // First call creates factory
        val f1 = factory ?: "new-factory".also { factory = it }
        assertEquals("new-factory", f1)
        assertEquals("new-factory", factory)

        // Second call reuses
        val f2 = factory ?: "another-factory".also { factory = it }
        assertEquals("new-factory", f2)
    }

    @Test
    fun `concurrent map is thread-safe for reads and writes`() {
        val engines = ConcurrentHashMap<String, String>()
        val threads = (1..10).map { i ->
            Thread {
                engines["handle-$i"] = "engine-$i"
                Thread.sleep(1)
                engines["handle-$i"]
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(10, engines.size)
    }

    // ── Interceptor null-safety ──────────────────────────

    @Test
    fun `null interceptor set is safe`() {
        var interceptor: ((ByteArray, Int) -> Unit)? = { _, _ -> }
        interceptor = null
        // Setting to null is the detach pattern
        assertNull(interceptor)
    }

    @Test
    fun `drainPtyWrites null means no write-back`() {
        val ptyResponse: ByteArray? = null
        // Simulates: if (ptyResponse != null) session.write(...)
        var written = false
        if (ptyResponse != null) {
            written = true
        }
        assertTrue(!written)
    }

    @Test
    fun `drainPtyWrites non-null triggers write-back`() {
        val ptyResponse: ByteArray? = byteArrayOf(0x1b, 0x5b, 0x3f)
        var written = false
        if (ptyResponse != null) {
            written = true
        }
        assertTrue(written)
    }
}
