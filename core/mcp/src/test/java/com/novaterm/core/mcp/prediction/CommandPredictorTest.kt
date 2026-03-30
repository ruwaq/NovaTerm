package com.novaterm.core.mcp.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandPredictorTest {

    private lateinit var predictor: CommandPredictor

    @Before
    fun setup() {
        predictor = CommandPredictor()
    }

    // ── Learning ────────────────────────────────────────────

    @Test
    fun `learn increments unigram count`() {
        predictor.learn("ls")
        predictor.learn("ls")
        predictor.learn("cd")

        assertEquals(2, predictor.vocabularySize)
        assertEquals(3, predictor.totalLearned)
    }

    @Test
    fun `learn with previous command creates bigram`() {
        predictor.learn("git status", previousCommand = "git add .")
        val predictions = predictor.predict(previousCommand = "git add .")

        assertTrue(predictions.isNotEmpty())
        assertEquals("git status", predictions.first().command)
    }

    @Test
    fun `learn normalizes whitespace`() {
        predictor.learn("  ls   -la  ")
        predictor.learn("ls -la")

        assertEquals(1, predictor.vocabularySize)
        assertEquals(2, predictor.totalLearned)
    }

    @Test
    fun `learn ignores blank commands`() {
        predictor.learn("")
        predictor.learn("   ")

        assertEquals(0, predictor.vocabularySize)
        assertEquals(0, predictor.totalLearned)
    }

    @Test
    fun `learn tracks CWD context`() {
        predictor.learn("cargo build", cwd = "/home/user/projects/novaterm")
        predictor.learn("cargo test", cwd = "/home/user/projects/novaterm")
        predictor.learn("npm install", cwd = "/home/user/projects/webapp")

        val predictions = predictor.predict(cwd = "/home/user/projects/novaterm")
        val commands = predictions.map { it.command }
        assertTrue("cargo build" in commands)
        assertTrue("cargo test" in commands)
    }

    // ── Prediction ──────────────────────────────────────────

    @Test
    fun `predict returns empty for fresh predictor`() {
        val predictions = predictor.predict()
        assertTrue(predictions.isEmpty())
    }

    @Test
    fun `predict with bigram gives highest confidence`() {
        // git add → git commit happens 5 times
        repeat(5) {
            predictor.learn("git commit", previousCommand = "git add .")
        }
        // git add → git status happens 1 time
        predictor.learn("git status", previousCommand = "git add .")

        val predictions = predictor.predict(previousCommand = "git add .")
        assertEquals("git commit", predictions.first().command)
    }

    @Test
    fun `predict filters by prefix`() {
        predictor.learn("git status")
        predictor.learn("git add .")
        predictor.learn("grep foo")
        predictor.learn("cat file.txt")

        val predictions = predictor.predict(prefix = "git")
        val commands = predictions.map { it.command }
        assertTrue(commands.all { it.startsWith("git") })
        assertTrue("cat file.txt" !in commands)
    }

    @Test
    fun `predict prefix is case insensitive`() {
        predictor.learn("Git status")
        val predictions = predictor.predict(prefix = "git")
        assertTrue(predictions.isNotEmpty())
    }

    @Test
    fun `predict respects maxResults`() {
        repeat(10) { i -> predictor.learn("cmd$i") }

        val predictions = predictor.predict(maxResults = 3)
        assertEquals(3, predictions.size)
    }

    @Test
    fun `predict sorted by confidence descending`() {
        predictor.learn("ls")
        predictor.learn("ls")
        predictor.learn("ls")
        predictor.learn("cd")

        val predictions = predictor.predict()
        assertTrue(predictions.size >= 2)
        assertTrue(predictions[0].confidence >= predictions[1].confidence)
        assertEquals("ls", predictions[0].command)
    }

    @Test
    fun `predict combines bigram and unigram scores`() {
        // Strong unigram: ls appears many times
        repeat(20) { predictor.learn("ls") }
        // Weak bigram: cd → pwd once
        predictor.learn("pwd", previousCommand = "cd")

        val predictions = predictor.predict(previousCommand = "cd")
        // pwd should rank high due to bigram, ls should also appear via unigram
        val commands = predictions.map { it.command }
        assertTrue("pwd" in commands)
        assertTrue("ls" in commands)
    }

    @Test
    fun `predict with CWD boosts directory-relevant commands`() {
        // npm commands in webapp dir
        repeat(10) {
            predictor.learn("npm test", cwd = "/projects/webapp")
        }
        // cargo commands in rust dir
        repeat(10) {
            predictor.learn("cargo test", cwd = "/projects/rust")
        }

        val webPredictions = predictor.predict(cwd = "/projects/webapp")
        val rustPredictions = predictor.predict(cwd = "/projects/rust")

        // npm test should rank higher in webapp context
        val npmRankWeb = webPredictions.indexOfFirst { it.command == "npm test" }
        val cargoRankWeb = webPredictions.indexOfFirst { it.command == "cargo test" }
        assertTrue(npmRankWeb < cargoRankWeb)

        // cargo test should rank higher in rust context
        val cargoRankRust = rustPredictions.indexOfFirst { it.command == "cargo test" }
        val npmRankRust = rustPredictions.indexOfFirst { it.command == "npm test" }
        assertTrue(cargoRankRust < npmRankRust)
    }

    // ── History loading ─────────────────────────────────────

    @Test
    fun `loadHistory parses bash history format`() {
        val history = listOf(
            "ls",
            "cd projects",
            "git status",
            "# comment line",
            "",
            "git add .",
            "git commit -m 'init'",
        )
        predictor.loadHistory(history)

        assertEquals(5, predictor.vocabularySize)
        assertEquals(5, predictor.totalLearned)
    }

    @Test
    fun `loadHistory creates sequential bigrams`() {
        val history = listOf("cd projects", "ls", "git status")
        predictor.loadHistory(history)

        // cd projects → ls
        val afterCd = predictor.predict(previousCommand = "cd projects")
        assertEquals("ls", afterCd.first().command)

        // ls → git status
        val afterLs = predictor.predict(previousCommand = "ls")
        assertEquals("git status", afterLs.first().command)
    }

    @Test
    fun `loadHistory skips comments and blanks`() {
        val history = listOf(
            "# timestamp 1234567890",
            "",
            "   ",
            "ls",
        )
        predictor.loadHistory(history)

        assertEquals(1, predictor.vocabularySize)
    }

    // ── Clear ───────────────────────────────────────────────

    @Test
    fun `clear resets all state`() {
        predictor.learn("ls", previousCommand = "cd", cwd = "/home")
        predictor.clear()

        assertEquals(0, predictor.vocabularySize)
        assertEquals(0, predictor.totalLearned)
        assertTrue(predictor.predict().isEmpty())
        assertTrue(predictor.predict(previousCommand = "cd").isEmpty())
        assertTrue(predictor.predict(cwd = "/home").isEmpty())
    }

    // ── Export / Import ─────────────────────────────────────

    @Test
    fun `export and import preserves state`() {
        predictor.learn("ls", previousCommand = "cd", cwd = "/home/user")
        predictor.learn("git status", previousCommand = "ls")
        predictor.learn("ls")

        val unigrams = predictor.exportUnigrams()
        val bigrams = predictor.exportBigrams()
        val cwdCommands = predictor.exportCwdCommands()
        val total = predictor.totalLearned

        val restored = CommandPredictor()
        restored.importState(unigrams, bigrams, cwdCommands, total)

        assertEquals(predictor.vocabularySize, restored.vocabularySize)
        assertEquals(predictor.totalLearned, restored.totalLearned)

        // Predictions should match
        val original = predictor.predict(previousCommand = "cd")
        val restoredPred = restored.predict(previousCommand = "cd")
        assertEquals(original.map { it.command }, restoredPred.map { it.command })
    }

    @Test
    fun `importState clears previous data`() {
        predictor.learn("old_command")
        predictor.importState(
            unigrams = mapOf("new_command" to 1),
            bigrams = emptyMap(),
            cwdCommands = emptyMap(),
            totalCommands = 1,
        )

        assertEquals(1, predictor.vocabularySize)
        val predictions = predictor.predict()
        assertEquals("new_command", predictions.first().command)
    }

    // ── CWD shortening ──────────────────────────────────────

    @Test
    fun `CWD shortening groups similar paths`() {
        // Both resolve to "projects/novaterm" internally
        predictor.learn("cargo build", cwd = "/home/user/projects/novaterm")
        predictor.learn("cargo test", cwd = "/data/data/com.termux/files/home/projects/novaterm")

        val predictions = predictor.predict(cwd = "/any/prefix/projects/novaterm")
        val commands = predictions.map { it.command }
        assertTrue("cargo build" in commands)
        assertTrue("cargo test" in commands)
    }
}
