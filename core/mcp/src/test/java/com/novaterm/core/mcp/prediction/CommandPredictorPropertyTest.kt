package com.novaterm.core.mcp.prediction

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Property-based tests using Kotest's property testing framework.
 *
 * Each property is verified against 1000 randomly generated inputs.
 * These tests find edge cases that hand-written tests miss:
 * unicode, empty strings, very long strings, special characters, etc.
 */
class CommandPredictorPropertyTest {

    // ── Invariant: totalLearned counts valid commands ────────

    @Test
    fun `totalLearned equals count of non-blank commands`() = runTest {
        checkAll(Arb.list(Arb.string(0..100), 0..50)) { commands ->
            val predictor = CommandPredictor()
            commands.forEach { predictor.learn(it) }

            val validCount = commands.count { it.trim().isNotBlank() }
            assertEquals(
                "totalLearned should match valid command count",
                validCount,
                predictor.totalLearned,
            )
        }
    }

    // ── Invariant: vocabulary size <= totalLearned ───────────

    @Test
    fun `vocabularySize never exceeds totalLearned`() = runTest {
        checkAll(Arb.list(Arb.string(1..50), 1..100)) { commands ->
            val predictor = CommandPredictor()
            commands.forEach { predictor.learn(it) }

            assertTrue(
                "vocabularySize (${predictor.vocabularySize}) should be <= totalLearned (${predictor.totalLearned})",
                predictor.vocabularySize <= predictor.totalLearned,
            )
        }
    }

    // ── Invariant: learned commands are always findable ──────

    @Test
    fun `learned command is always predicted with its full text as prefix`() = runTest {
        checkAll(Arb.string(1..80).filter { it.trim().isNotBlank() }) { command ->
            val predictor = CommandPredictor()
            predictor.learn(command)

            val normalized = command.trim().replace(Regex("\\s+"), " ")
            val predictions = predictor.predict(prefix = normalized, maxResults = 100)
            val found = predictions.any { it.command == normalized }
            assertTrue(
                "Learned command '$normalized' should be predicted with exact prefix",
                found,
            )
        }
    }

    // ── Invariant: confidence is always in [0, 1] ───────────

    @Test
    fun `confidence scores are always in valid range`() = runTest {
        checkAll(
            Arb.list(Arb.string(1..30).filter { it.trim().isNotBlank() }, 1..50),
        ) { commands ->
            val predictor = CommandPredictor()
            var prev: String? = null
            commands.forEach {
                predictor.learn(it, previousCommand = prev, cwd = "/test/dir")
                prev = it
            }

            val predictions = predictor.predict(
                previousCommand = prev,
                cwd = "/test/dir",
                maxResults = 20,
            )
            predictions.forEach { pred ->
                assertTrue(
                    "Confidence ${pred.confidence} should be >= 0",
                    pred.confidence >= 0.0,
                )
                assertTrue(
                    "Confidence ${pred.confidence} should be <= 1",
                    pred.confidence <= 1.0,
                )
                assertTrue(
                    "Confidence should be finite",
                    pred.confidence.isFinite(),
                )
            }
        }
    }

    // ── Invariant: predictions are sorted by confidence ─────

    @Test
    fun `predictions are always sorted descending by confidence`() = runTest {
        checkAll(
            Arb.list(Arb.string(1..20).filter { it.trim().isNotBlank() }, 5..50),
        ) { commands ->
            val predictor = CommandPredictor()
            commands.forEach { predictor.learn(it) }

            val predictions = predictor.predict(maxResults = 20)
            for (i in 0 until predictions.size - 1) {
                assertTrue(
                    "Prediction at $i (${predictions[i].confidence}) should be >= at ${i + 1} (${predictions[i + 1].confidence})",
                    predictions[i].confidence >= predictions[i + 1].confidence,
                )
            }
        }
    }

    // ── Invariant: maxResults is respected ──────────────────

    @Test
    fun `predict never returns more than maxResults`() = runTest {
        checkAll(
            Arb.list(Arb.string(1..20).filter { it.trim().isNotBlank() }, 1..100),
            Arb.int(1..10),
        ) { commands, maxResults ->
            val predictor = CommandPredictor()
            commands.forEach { predictor.learn(it) }

            val predictions = predictor.predict(maxResults = maxResults)
            assertTrue(
                "Got ${predictions.size} predictions, expected <= $maxResults",
                predictions.size <= maxResults,
            )
        }
    }

    // ── Invariant: prefix filter is exact ───────────────────

    @Test
    fun `all predictions match the given prefix`() = runTest {
        checkAll(
            Arb.list(Arb.string(1..30).filter { it.trim().isNotBlank() }, 5..50),
        ) { commands ->
            val predictor = CommandPredictor()
            commands.forEach { predictor.learn(it) }

            // Use the first 2 chars of a random command as prefix
            val prefix = commands.first().trim().take(2)
            if (prefix.isNotBlank()) {
                val predictions = predictor.predict(prefix = prefix)
                predictions.forEach { pred ->
                    assertTrue(
                        "Prediction '${pred.command}' should start with '$prefix'",
                        pred.command.startsWith(prefix, ignoreCase = true),
                    )
                }
            }
        }
    }

    // ── Invariant: clear resets everything ───────────────────

    @Test
    fun `clear always resets to zero state`() = runTest {
        checkAll(
            Arb.list(Arb.string(1..30).filter { it.trim().isNotBlank() }, 1..50),
        ) { commands ->
            val predictor = CommandPredictor()
            commands.forEach { predictor.learn(it, cwd = "/dir") }

            predictor.clear()
            assertEquals(0, predictor.vocabularySize)
            assertEquals(0, predictor.totalLearned)
            assertTrue(predictor.predict().isEmpty())
        }
    }

    // ── Invariant: export/import round-trip is lossless ─────

    @Test
    fun `export then import preserves predictions exactly`() = runTest {
        checkAll(
            Arb.list(Arb.string(1..30).filter { it.trim().isNotBlank() }, 3..30),
        ) { commands ->
            val original = CommandPredictor()
            var prev: String? = null
            commands.forEach {
                original.learn(it, previousCommand = prev, cwd = "/dir")
                prev = it
            }

            val restored = CommandPredictor()
            restored.importState(
                original.exportUnigrams(),
                original.exportBigrams(),
                original.exportCwdCommands(),
                original.totalLearned,
            )

            assertEquals(original.vocabularySize, restored.vocabularySize)
            assertEquals(original.totalLearned, restored.totalLearned)

            // Predictions should match exactly
            val origPreds = original.predict(previousCommand = prev, cwd = "/dir")
            val restPreds = restored.predict(previousCommand = prev, cwd = "/dir")
            assertEquals(
                origPreds.map { it.command },
                restPreds.map { it.command },
            )
        }
    }

    // ── Invariant: learn never throws ───────────────────────

    @Test
    fun `learn never throws regardless of input`() = runTest {
        checkAll(
            Arb.string(0..500), // includes empty, whitespace, unicode, null bytes
            Arb.string(0..200),
            Arb.string(0..200),
        ) { command, prevCommand, cwd ->
            val predictor = CommandPredictor()
            // Should NEVER throw
            predictor.learn(command, previousCommand = prevCommand, cwd = cwd)
        }
    }
}
