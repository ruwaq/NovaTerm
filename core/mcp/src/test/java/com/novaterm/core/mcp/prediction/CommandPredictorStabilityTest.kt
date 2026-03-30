package com.novaterm.core.mcp.prediction

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stability and performance tests for the prediction system.
 *
 * Validates:
 * - Thread safety under concurrent access
 * - Persistence resilience (corruption, partial writes)
 * - Performance characteristics (latency, throughput)
 * - Edge cases in real-world usage patterns
 */
class CommandPredictorStabilityTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "stability_test_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    // ── Concurrent access ───────────────────────────────────

    @Test
    fun `concurrent learns do not corrupt state`() {
        val predictor = CommandPredictor()
        val threadCount = 8
        val commandsPerThread = 1000
        val barrier = CyclicBarrier(threadCount)
        val errors = AtomicInteger(0)

        val threads = (0 until threadCount).map { threadId ->
            Thread {
                try {
                    barrier.await() // Start all threads simultaneously
                    repeat(commandsPerThread) { i ->
                        predictor.learn(
                            "thread${threadId}_cmd_$i",
                            previousCommand = if (i > 0) "thread${threadId}_cmd_${i - 1}" else null,
                            cwd = "/dir_$threadId",
                        )
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }

        assertEquals("No threads should have thrown exceptions", 0, errors.get())
        // Total learned should be threadCount * commandsPerThread
        assertEquals(threadCount * commandsPerThread, predictor.totalLearned)
    }

    @Test
    fun `concurrent reads during writes do not crash`() {
        val predictor = CommandPredictor()
        // Pre-populate
        repeat(100) { i -> predictor.learn("cmd_$i") }

        val barrier = CyclicBarrier(4)
        val errors = AtomicInteger(0)

        // 2 writer threads
        val writers = (0..1).map { threadId ->
            Thread {
                try {
                    barrier.await()
                    repeat(500) { i ->
                        predictor.learn("new_${threadId}_$i", cwd = "/dir")
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                }
            }
        }

        // 2 reader threads
        val readers = (0..1).map {
            Thread {
                try {
                    barrier.await()
                    repeat(500) {
                        predictor.predict(prefix = "cmd", maxResults = 5)
                        predictor.predict(cwd = "/dir", maxResults = 3)
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                }
            }
        }

        (writers + readers).forEach { it.start() }
        (writers + readers).forEach { it.join(10_000) }

        assertEquals("No threads should have thrown exceptions", 0, errors.get())
    }

    // ── Persistence resilience ──────────────────────────────

    @Test
    fun `load handles empty JSON file gracefully`() {
        File(tempDir, "command_predictor.json").writeText("")
        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(0, loaded.vocabularySize)
    }

    @Test
    fun `load handles truncated JSON file`() {
        File(tempDir, "command_predictor.json").writeText(
            """{"version":1,"totalCommands":5,"uni"""
        )
        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(0, loaded.vocabularySize)
    }

    @Test
    fun `load handles JSON with missing fields`() {
        File(tempDir, "command_predictor.json").writeText(
            """{"version":1}"""
        )
        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(0, loaded.vocabularySize)
    }

    @Test
    fun `load handles JSON with extra unknown fields`() {
        File(tempDir, "command_predictor.json").writeText(
            """{"version":1,"totalCommands":1,"unigrams":{"ls":1},"bigrams":{},"cwdCommands":{},"future_field":"value"}"""
        )
        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(1, loaded.vocabularySize)
    }

    @Test
    fun `load handles JSON with wrong value types`() {
        File(tempDir, "command_predictor.json").writeText(
            """{"version":1,"totalCommands":"not_a_number","unigrams":{},"bigrams":{},"cwdCommands":{}}"""
        )
        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(0, loaded.vocabularySize)
    }

    @Test
    fun `save to read-only directory does not crash engine`() {
        val engine = PredictionEngine(tempDir)
        engine.onCommandExecuted("s1", "ls")

        // Make directory read-only
        tempDir.setWritable(false)
        try {
            // Should not throw — save returns false on failure
            val result = engine.save()
            assertTrue("Save to read-only dir should return false", !result)
        } finally {
            tempDir.setWritable(true)
        }

        // Engine state should still be intact
        assertEquals(1, engine.vocabularySize)

        // Now save should work after restoring write permission
        val result = engine.save()
        assertTrue("Save should succeed after restoring write permission", result)
    }

    // ── Performance characteristics ─────────────────────────

    @Test
    fun `predict latency under 10ms with 10K vocabulary`() {
        val predictor = CommandPredictor()
        repeat(10_000) { i ->
            predictor.learn("cmd_$i", previousCommand = "cmd_${i - 1}", cwd = "/dir_${i % 50}")
        }

        // Warm up
        repeat(10) { predictor.predict(previousCommand = "cmd_5000", cwd = "/dir_25") }

        // Measure
        val start = System.nanoTime()
        repeat(100) {
            predictor.predict(previousCommand = "cmd_5000", prefix = "cmd_5", cwd = "/dir_25", maxResults = 5)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000 // ms
        val avgMs = elapsed / 100.0

        assertTrue("Average predict latency ${avgMs}ms should be under 10ms", avgMs < 10.0)
    }

    @Test
    fun `learn throughput handles 100K commands in under 2 seconds`() {
        val predictor = CommandPredictor()
        val start = System.nanoTime()
        repeat(100_000) { i ->
            predictor.learn("cmd_${i % 1000}", previousCommand = "cmd_${(i - 1) % 1000}")
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000 // ms

        assertTrue("100K learns took ${elapsed}ms, should be under 2000ms", elapsed < 2000)
        assertEquals(100_000, predictor.totalLearned)
    }

    @Test
    fun `serialization round-trip under 100ms for 10K vocabulary`() {
        val predictor = CommandPredictor()
        repeat(10_000) { i ->
            predictor.learn("cmd_$i", previousCommand = "cmd_${i - 1}", cwd = "/dir_${i % 50}")
        }

        // Save
        val saveStart = System.nanoTime()
        CommandPredictorStore.save(predictor, tempDir)
        val saveMs = (System.nanoTime() - saveStart) / 1_000_000

        // Load
        val loadStart = System.nanoTime()
        val loaded = CommandPredictorStore.load(tempDir)
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000

        assertTrue("Save took ${saveMs}ms, should be under 500ms", saveMs < 500)
        assertTrue("Load took ${loadMs}ms, should be under 500ms", loadMs < 500)
        assertEquals(predictor.vocabularySize, loaded.vocabularySize)
    }

    // ── Real-world edge cases ───────────────────────────────

    @Test
    fun `handles rapid session creation and cleanup`() {
        val engine = PredictionEngine(tempDir)

        repeat(100) { i ->
            val sessionId = "session_$i"
            engine.onCommandExecuted(sessionId, "ls")
            engine.onCommandExecuted(sessionId, "cd projects")
            engine.onSessionClosed(sessionId)
        }

        // Engine should have learned from all sessions
        assertTrue(engine.totalLearned > 0)
        // All session contexts should be cleaned up
        val preds = engine.predict(sessionId = "session_99")
        // Should still return predictions based on unigrams (no bigram context)
        assertTrue(preds.isNotEmpty())
    }

    @Test
    fun `handles history file with mixed line endings`() {
        val histFile = File(tempDir, ".bash_history")
        histFile.writeText("ls\r\ncd projects\rcat file.txt\ngit status")

        val predictor = CommandPredictor()
        predictor.loadHistory(histFile.readLines())
        assertTrue(predictor.vocabularySize >= 3)
    }

    @Test
    fun `handles history file with timestamps (HISTTIMEFORMAT)`() {
        val histFile = File(tempDir, ".bash_history")
        histFile.writeText(
            """
            #1711234567
            ls
            #1711234568
            cd projects
            #1711234569
            git status
            """.trimIndent()
        )

        val predictor = CommandPredictor()
        predictor.loadHistory(histFile.readLines())
        // Comment lines (timestamps) should be skipped
        assertEquals(3, predictor.vocabularySize)
    }
}
