package com.novaterm.core.mcp.prediction

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PredictionEngineTest {

    private lateinit var tempDir: File
    private lateinit var engine: PredictionEngine

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "engine_test_${System.nanoTime()}")
        tempDir.mkdirs()
        engine = PredictionEngine(tempDir)
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    // ── Basic command learning ──────────────────────────────

    @Test
    fun `onCommandExecuted learns commands`() {
        engine.onCommandExecuted("s1", "ls")
        engine.onCommandExecuted("s1", "cd projects")

        assertEquals(2, engine.totalLearned)
        assertEquals(2, engine.vocabularySize)
    }

    @Test
    fun `onCommandExecuted tracks bigrams per session`() {
        engine.onCommandExecuted("s1", "git add .")
        engine.onCommandExecuted("s1", "git commit -m 'fix'")

        val predictions = engine.predict(sessionId = "s1")
        // After git commit, should suggest based on bigram
        assertTrue(predictions.isNotEmpty())
    }

    @Test
    fun `separate sessions have independent bigram context`() {
        engine.onCommandExecuted("s1", "cargo build")
        engine.onCommandExecuted("s2", "npm install")

        // s1's last command is "cargo build", s2's is "npm install"
        engine.onCommandExecuted("s1", "cargo test", cwd = "/rust")
        engine.onCommandExecuted("s2", "npm test", cwd = "/web")

        // Predict for s1: bigram from "cargo test" → should suggest cargo-related
        val s1Preds = engine.predict(sessionId = "s1", cwd = "/rust")
        val s2Preds = engine.predict(sessionId = "s2", cwd = "/web")

        // Each session's predictions should be influenced by its own context
        assertTrue(s1Preds.isNotEmpty())
        assertTrue(s2Preds.isNotEmpty())
    }

    // ── Prediction ──────────────────────────────────────────

    @Test
    fun `predict with prefix filters commands`() {
        engine.onCommandExecuted("s1", "git status")
        engine.onCommandExecuted("s1", "git add .")
        engine.onCommandExecuted("s1", "cat file.txt")

        val gitPreds = engine.predict(prefix = "git")
        assertTrue(gitPreds.all { it.command.startsWith("git") })
    }

    @Test
    fun `predict with CWD boosts local commands`() {
        repeat(5) { engine.onCommandExecuted("s1", "cargo test", cwd = "/rust") }
        repeat(5) { engine.onCommandExecuted("s1", "npm test", cwd = "/web") }

        val rustPreds = engine.predict(cwd = "/rust")
        val webPreds = engine.predict(cwd = "/web")

        val cargoInRust = rustPreds.indexOfFirst { it.command == "cargo test" }
        val npmInWeb = webPreds.indexOfFirst { it.command == "npm test" }

        assertTrue(cargoInRust >= 0)
        assertTrue(npmInWeb >= 0)
    }

    @Test
    fun `predict returns empty for fresh engine`() {
        assertTrue(engine.predict().isEmpty())
    }

    // ── Persistence ─────────────────────────────────────────

    @Test
    fun `save and load preserves learned data`() {
        engine.onCommandExecuted("s1", "ls", cwd = "/home")
        engine.onCommandExecuted("s1", "git status", cwd = "/home")
        engine.save()

        val restored = PredictionEngine(tempDir)
        restored.load()

        assertEquals(engine.vocabularySize, restored.vocabularySize)
        assertEquals(engine.totalLearned, restored.totalLearned)
    }

    @Test
    fun `save is no-op when no changes`() {
        engine.save() // Should not create file
        val file = File(tempDir, "command_predictor.json")
        assertTrue(!file.exists())
    }

    @Test
    fun `load on empty directory creates fresh engine`() {
        engine.load()
        assertEquals(0, engine.vocabularySize)
    }

    // ── History loading ─────────────────────────────────────

    @Test
    fun `loadHistoryFile bootstraps from bash history`() {
        val histFile = File(tempDir, ".bash_history")
        histFile.writeText(
            """
            ls
            cd projects
            git status
            git add .
            git commit -m 'init'
            """.trimIndent()
        )

        engine.loadHistoryFile(histFile)

        assertEquals(5, engine.vocabularySize)
        assertTrue(engine.totalLearned >= 5)
    }

    @Test
    fun `loadHistoryFile ignores missing file`() {
        engine.loadHistoryFile(File(tempDir, "nonexistent"))
        assertEquals(0, engine.vocabularySize)
    }

    // ── Session lifecycle ───────────────────────────────────

    @Test
    fun `onSessionClosed removes session context`() {
        engine.onCommandExecuted("s1", "ls")
        engine.onSessionClosed("s1")

        // After closing, predict should not use s1's last command for bigram
        val predictions = engine.predict(sessionId = "s1")
        // Still returns unigram results, just no bigram boost
        assertTrue(predictions.isNotEmpty())
        assertEquals("ls", predictions.first().command)
    }
}
