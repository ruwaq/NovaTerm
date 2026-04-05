package com.novaterm.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LlmConfigTest {

    @Test
    fun `default config has sensible values`() {
        val config = LlmConfig()

        assertEquals("", config.modelPath)
        assertEquals(0.3f, config.temperature, 0.001f)
        assertEquals(64, config.maxTokens)
        assertEquals(10, config.contextDepth)
        assertEquals(500, config.maxOutputChars)
        assertEquals(2_000L, config.inferenceTimeoutMs)
        assertEquals(4, config.numThreads)
        assertEquals(ModelCatalog.ModelFamily.GEMMA4, config.modelFamily)
    }

    @Test
    fun `modelExists returns false for empty path`() {
        val config = LlmConfig(modelPath = "")
        assertFalse(config.modelExists)
    }

    @Test
    fun `modelExists returns false for nonexistent path`() {
        val config = LlmConfig(modelPath = "/nonexistent/model.gguf")
        assertFalse(config.modelExists)
    }

    @Test
    fun `modelExists returns true for existing file`() {
        val tmp = File.createTempFile("test_model", ".gguf")
        try {
            val config = LlmConfig(modelPath = tmp.absolutePath)
            assertTrue(config.modelExists)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `constants reflect Gemma 4 E2B`() {
        assertEquals(1500, LlmConfig.MODEL_SIZE_MB)
        assertEquals(1800, LlmConfig.MODEL_RAM_MB)
        assertTrue(LlmConfig.MODEL_URL.contains("huggingface"))
        assertTrue(LlmConfig.MODEL_URL.contains("gemma-4"))
        assertTrue(LlmConfig.DEFAULT_MODEL_NAME.contains("gemma-4"))
        assertTrue(LlmConfig.DEFAULT_MODEL_NAME.endsWith(".gguf"))
    }
}
