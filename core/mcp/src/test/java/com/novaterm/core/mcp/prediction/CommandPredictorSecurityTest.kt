package com.novaterm.core.mcp.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Security-focused tests for CommandPredictor.
 *
 * Ensures the predictor handles adversarial input safely:
 * - No command injection through learned commands
 * - No crashes on malformed input
 * - No unbounded memory growth
 * - Predictable behavior with unicode/special chars
 */
class CommandPredictorSecurityTest {

    private lateinit var predictor: CommandPredictor

    @Before
    fun setup() {
        predictor = CommandPredictor()
    }

    // ── Input sanitization ──────────────────────────────────

    @Test
    fun `handles null-byte in commands`() {
        predictor.learn("ls\u0000 -la")
        assertEquals(1, predictor.vocabularySize)
        val preds = predictor.predict()
        assertTrue(preds.isNotEmpty())
    }

    @Test
    fun `handles newlines in commands`() {
        predictor.learn("echo 'hello\nworld'")
        assertEquals(1, predictor.vocabularySize)
    }

    @Test
    fun `handles extremely long command`() {
        val longCmd = "a".repeat(10_000)
        predictor.learn(longCmd)
        assertEquals(1, predictor.vocabularySize)

        val preds = predictor.predict(prefix = "a")
        assertTrue(preds.isNotEmpty())
        assertEquals(longCmd, preds.first().command)
    }

    @Test
    fun `handles unicode commands`() {
        predictor.learn("echo '日本語テスト'")
        predictor.learn("echo '🚀🔥💻'")
        predictor.learn("echo 'مرحبا'")
        assertEquals(3, predictor.vocabularySize)

        val preds = predictor.predict(prefix = "echo")
        assertEquals(3, preds.size)
    }

    @Test
    fun `handles escape sequences in commands`() {
        predictor.learn("echo -e '\\033[31mRED\\033[0m'")
        predictor.learn("printf '\\x1b[32m'")
        assertEquals(2, predictor.vocabularySize)
    }

    @Test
    fun `handles special shell characters`() {
        val specialCommands = listOf(
            "echo \$HOME",
            "ls | grep foo",
            "cat << 'EOF'",
            "cmd1 && cmd2 || cmd3",
            "echo `whoami`",
            "ls > /dev/null 2>&1",
            "find . -name '*.kt' -exec rm {} \\;",
            "echo \"\$(date)\"",
        )
        specialCommands.forEach { predictor.learn(it) }
        assertEquals(specialCommands.size, predictor.vocabularySize)
    }

    @Test
    fun `handles path traversal in CWD`() {
        predictor.learn("ls", cwd = "/home/../../../etc/passwd")
        predictor.learn("ls", cwd = "/home/user/../../../../root")
        // Should not crash, just bucket by last 2 segments
        val preds = predictor.predict(cwd = "/etc/passwd")
        assertTrue(preds.isNotEmpty())
    }

    // ── Memory safety ───────────────────────────────────────

    @Test
    fun `handles high cardinality commands without OOM`() {
        // Simulate 10K unique commands (realistic heavy user)
        repeat(10_000) { i ->
            predictor.learn("cmd_$i", previousCommand = "cmd_${i - 1}", cwd = "/dir_${i % 100}")
        }
        assertEquals(10_000, predictor.vocabularySize)

        // Predictions should still be fast and bounded
        val preds = predictor.predict(maxResults = 5)
        assertTrue(preds.size <= 5)
    }

    @Test
    fun `duplicate commands do not grow memory linearly`() {
        // Same command 100K times should only store 1 unigram entry
        repeat(100_000) {
            predictor.learn("ls -la")
        }
        assertEquals(1, predictor.vocabularySize)
        assertEquals(100_000, predictor.totalLearned)
    }

    @Test
    fun `clear truly releases memory`() {
        repeat(1000) { i ->
            predictor.learn("cmd_$i", previousCommand = "cmd_${i - 1}", cwd = "/dir_$i")
        }
        assertTrue(predictor.vocabularySize > 0)

        predictor.clear()
        assertEquals(0, predictor.vocabularySize)
        assertEquals(0, predictor.totalLearned)
        assertTrue(predictor.exportUnigrams().isEmpty())
        assertTrue(predictor.exportBigrams().isEmpty())
        assertTrue(predictor.exportCwdCommands().isEmpty())
    }

    // ── Prediction bounds ───────────────────────────────────

    @Test
    fun `confidence scores are bounded between 0 and 1`() {
        repeat(100) { i ->
            predictor.learn("cmd_$i", previousCommand = "cmd_${i - 1}", cwd = "/dir")
        }
        val preds = predictor.predict(
            previousCommand = "cmd_50",
            cwd = "/dir",
            maxResults = 20,
        )
        preds.forEach { pred ->
            assertTrue("Confidence ${pred.confidence} should be >= 0", pred.confidence >= 0.0)
            assertTrue("Confidence ${pred.confidence} should be <= 1", pred.confidence <= 1.0)
        }
    }

    @Test
    fun `maxResults 0 returns empty list`() {
        predictor.learn("ls")
        val preds = predictor.predict(maxResults = 0)
        assertTrue(preds.isEmpty())
    }

    @Test
    fun `maxResults negative returns empty list`() {
        predictor.learn("ls")
        val preds = predictor.predict(maxResults = -1)
        assertTrue(preds.isEmpty())
    }
}
