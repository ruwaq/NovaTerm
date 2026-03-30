package com.novaterm.core.mcp.prediction

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * End-to-end integration test for the suggestion flow:
 * command execution → learning → prediction → accept/dismiss.
 *
 * Simulates what happens when a developer uses the terminal:
 * they execute commands, and the engine suggests the next one.
 */
class SuggestionFlowTest {

    private lateinit var tempDir: File
    private lateinit var engine: PredictionEngine

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "suggestion_test_${System.nanoTime()}")
        tempDir.mkdirs()
        engine = PredictionEngine(tempDir)
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `typical git workflow builds suggestions`() {
        val session = "session_0"
        val cwd = "/home/user/projects/myapp"

        // Simulate a typical git workflow repeated several times
        repeat(5) {
            engine.onCommandExecuted(session, "git status", cwd)
            engine.onCommandExecuted(session, "git add .", cwd)
            engine.onCommandExecuted(session, "git commit -m 'update'", cwd)
            engine.onCommandExecuted(session, "git push", cwd)
        }

        // After "git push" (last command), the engine should predict git commands
        val preds = engine.predict(sessionId = session, cwd = cwd)
        assertNotNull(preds.firstOrNull())
        // Top prediction should be a git command (from the repeated workflow)
        assertTrue(preds.first().command.startsWith("git"))

        // After "git add .", should suggest "git commit" (strong bigram)
        engine.onCommandExecuted(session, "git add .", cwd)
        val afterAdd = engine.predict(sessionId = session, cwd = cwd)
        assertEquals("git commit -m 'update'", afterAdd.first().command)
    }

    @Test
    fun `prefix filtering works during typing`() {
        val session = "session_0"

        // Teach several commands
        repeat(3) {
            engine.onCommandExecuted(session, "git status")
            engine.onCommandExecuted(session, "git add .")
            engine.onCommandExecuted(session, "grep -r TODO")
            engine.onCommandExecuted(session, "cargo build")
        }

        // Type "gi" — should only get git commands
        val giPreds = engine.predict(prefix = "gi")
        assertTrue(giPreds.all { it.command.startsWith("gi") })
        assertTrue(giPreds.size >= 2) // git status + git add

        // Type "car" — should only get cargo
        val carPreds = engine.predict(prefix = "car")
        assertTrue(carPreds.all { it.command.startsWith("car") })
    }

    @Test
    fun `CWD context gives project-relevant suggestions`() {
        val session = "session_0"

        // In Rust project: cargo commands
        repeat(10) {
            engine.onCommandExecuted(session, "cargo build", cwd = "/projects/rust-app")
            engine.onCommandExecuted(session, "cargo test", cwd = "/projects/rust-app")
        }

        // In Node project: npm commands
        repeat(10) {
            engine.onCommandExecuted(session, "npm run dev", cwd = "/projects/node-app")
            engine.onCommandExecuted(session, "npm test", cwd = "/projects/node-app")
        }

        // In rust dir, cargo should rank higher
        val rustPreds = engine.predict(cwd = "/projects/rust-app")
        val topRust = rustPreds.first().command
        assertTrue(topRust.startsWith("cargo"))

        // In node dir, npm should rank higher
        val nodePreds = engine.predict(cwd = "/projects/node-app")
        val topNode = nodePreds.first().command
        assertTrue(topNode.startsWith("npm"))
    }

    @Test
    fun `persistence survives restart`() {
        val session = "session_0"

        // Build up history
        repeat(5) {
            engine.onCommandExecuted(session, "ls", cwd = "/home")
            engine.onCommandExecuted(session, "cd projects", cwd = "/home")
        }
        engine.save()

        // Simulate app restart
        val restoredEngine = PredictionEngine(tempDir)
        restoredEngine.load()

        assertEquals(engine.vocabularySize, restoredEngine.vocabularySize)
        assertEquals(engine.totalLearned, restoredEngine.totalLearned)

        // Predictions still work after restore
        val preds = restoredEngine.predict(prefix = "ls")
        assertTrue(preds.isNotEmpty())
    }

    @Test
    fun `bootstrap from bash history gives immediate suggestions`() {
        val histFile = File(tempDir, ".bash_history")
        histFile.writeText(buildString {
            // Simulate a developer's real history
            repeat(20) {
                appendLine("cd ~/projects/myapp")
                appendLine("git status")
                appendLine("git add .")
                appendLine("git commit -m 'fix bug'")
                appendLine("npm test")
                appendLine("npm run build")
            }
        })

        engine.loadHistoryFile(histFile)

        // Should have learned the patterns
        assertTrue(engine.vocabularySize >= 5)
        assertTrue(engine.totalLearned >= 100)

        // Should predict based on history
        val preds = engine.predict(prefix = "git")
        assertTrue(preds.isNotEmpty())
    }

    @Test
    fun `multi-session isolation`() {
        // Build strong per-session patterns
        repeat(10) {
            engine.onCommandExecuted("s1", "git add .")
            engine.onCommandExecuted("s1", "git commit -m 'fix'")
        }
        repeat(10) {
            engine.onCommandExecuted("s2", "docker build .")
            engine.onCommandExecuted("s2", "docker run app")
        }

        // s1's last command is "git commit", bigram should suggest "git add ."
        val s1Preds = engine.predict(sessionId = "s1")
        // s2's last command is "docker run", bigram should suggest "docker build ."
        val s2Preds = engine.predict(sessionId = "s2")

        assertTrue(s1Preds.isNotEmpty())
        assertTrue(s2Preds.isNotEmpty())

        // Bigram context differs: s1 last="git commit" → "git add ."
        // s2 last="docker run" → "docker build ."
        val s1Top = s1Preds.first().command
        val s2Top = s2Preds.first().command
        assertEquals("git add .", s1Top)
        assertEquals("docker build .", s2Top)
    }

    @Test
    fun `empty predictor returns no suggestions`() {
        val preds = engine.predict(sessionId = "s1", prefix = "git")
        assertTrue(preds.isEmpty())
    }

    @Test
    fun `accept and dismiss flow`() {
        // Simulate ViewModel accept/dismiss
        engine.onCommandExecuted("s1", "ls")
        engine.onCommandExecuted("s1", "cd projects")
        engine.onCommandExecuted("s1", "ls")
        engine.onCommandExecuted("s1", "cd projects")
        engine.onCommandExecuted("s1", "ls")

        val preds = engine.predict(sessionId = "s1", maxResults = 1)
        assertTrue(preds.isNotEmpty())

        // "Accept" = use the suggestion
        val accepted = preds.first().command
        assertNotNull(accepted)

        // After accepting, feed it back to learn
        engine.onCommandExecuted("s1", accepted)
        // Engine still works
        assertTrue(engine.predict(sessionId = "s1").isNotEmpty())
    }
}
