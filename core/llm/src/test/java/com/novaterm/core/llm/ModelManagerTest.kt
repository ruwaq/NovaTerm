package com.novaterm.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ModelManager (non-Android logic).
 * DownloadManager integration requires instrumented tests.
 */
class ModelManagerTest {

    @Test
    fun `ModelCatalog has a default model`() {
        assertNotNull(ModelCatalog.DEFAULT)
        assertTrue(ModelCatalog.DEFAULT.isDefault)
        assertTrue(ModelCatalog.DEFAULT.url.contains("huggingface"))
    }

    @Test
    fun `ModelCatalog ALL contains default`() {
        assertTrue(ModelCatalog.ALL.any { it.isDefault })
        assertTrue(ModelCatalog.ALL.size >= 3)
    }

    @Test
    fun `ModelCatalog findById works`() {
        val found = ModelCatalog.findById("gemma-3-270m-q4")
        assertNotNull(found)
        assertEquals("Gemma 3 270M", found!!.displayName)
    }

    @Test
    fun `ModelCatalog findById returns null for unknown`() {
        assertNull(ModelCatalog.findById("nonexistent"))
    }

    @Test
    fun `all models have valid URLs`() {
        ModelCatalog.ALL.forEach { model ->
            assertTrue("${model.id} should have HF URL", model.url.startsWith("https://"))
            assertTrue("${model.id} should have filename", model.filename.isNotBlank())
            assertTrue("${model.id} should have size", model.sizeMb > 0)
            assertTrue("${model.id} should have RAM", model.ramMb > 0)
        }
    }

    @Test
    fun `models are sorted by size ascending`() {
        val sizes = ModelCatalog.ALL.map { it.sizeMb }
        assertEquals(sizes, sizes.sorted())
    }

    @Test
    fun `ModelState NotDownloaded has all fields`() {
        val state = ModelState.NotDownloaded(
            modelId = "test",
            displayName = "Test Model",
            sizeMb = 100,
            ramMb = 200,
            description = "A test model",
        )
        assertEquals("test", state.modelId)
        assertEquals(100, state.sizeMb)
    }

    @Test
    fun `ModelState Downloading progress calculation`() {
        val state = ModelState.Downloading(
            modelId = "test",
            displayName = "Test",
            bytesDownloaded = 50 * 1024 * 1024,
            totalBytes = 100 * 1024 * 1024,
        )
        assertEquals(50, state.progressPercent)
        assertEquals(50, state.downloadedMb)
        assertEquals(100, state.totalMb)
    }

    @Test
    fun `ModelState Downloading handles zero total`() {
        val state = ModelState.Downloading(
            modelId = "test",
            displayName = "Test",
            bytesDownloaded = 1000,
            totalBytes = 0,
        )
        assertEquals(0, state.progressPercent)
    }
}
