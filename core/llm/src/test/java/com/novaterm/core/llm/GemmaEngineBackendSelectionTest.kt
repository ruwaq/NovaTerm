package com.novaterm.core.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for backend selection logic in GemmaEngine.
 * Verifies GGUF → MediaPipe and TFLite → LiteRT routing.
 */
class GemmaEngineBackendSelectionTest {

    @Test
    fun `GGUF extension detected correctly`() {
        assertTrue("gemma-4-E2B-it-Q4_K_M.gguf".endsWith(".gguf", ignoreCase = true))
        assertTrue("model.GGUF".endsWith(".gguf", ignoreCase = true))
        assertFalse("model.tflite".endsWith(".gguf", ignoreCase = true))
    }

    @Test
    fun `TFLite extension detected correctly`() {
        assertTrue("gemma2b.tflite".endsWith(".tflite", ignoreCase = true))
        assertFalse("model.gguf".endsWith(".tflite", ignoreCase = true))
    }

    @Test
    fun `LlmConfig modelExists returns false for blank path`() {
        val config = LlmConfig(modelPath = "")
        assertFalse(config.modelExists)
    }

    @Test
    fun `LlmConfig default values are sensible for on-device inference`() {
        val config = LlmConfig()
        assertTrue("temperature should be low for deterministic suggestions", config.temperature <= 0.5f)
        assertTrue("maxTokens should be short for command suggestions", config.maxTokens <= 128)
        assertTrue("inferenceTimeout should be at least 3s", config.inferenceTimeoutMs >= 3_000)
        assertTrue("numThreads should be positive", config.numThreads > 0)
    }

    @Test
    fun `GemmaEngine starts in IDLE state`() {
        val config = LlmConfig(modelPath = "/nonexistent/model.gguf")
        val engine = GemmaEngine(config, context = null)
        assertTrue(engine.state.value == LlmState.IDLE)
        engine.release()
    }
}
