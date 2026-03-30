package com.novaterm.core.llm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelManagerTest {

    private lateinit var tempDir: File
    private lateinit var manager: ModelManager

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "llm_test_${System.nanoTime()}")
        tempDir.mkdirs()
        manager = ModelManager(tempDir)
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `isModelAvailable returns false when no model`() {
        assertFalse(manager.isModelAvailable())
    }

    @Test
    fun `isModelAvailable returns true when model exists`() {
        File(tempDir, LlmConfig.DEFAULT_MODEL_NAME).writeText("fake model")
        assertTrue(manager.isModelAvailable())
    }

    @Test
    fun `getModelPath points to correct location`() {
        val path = manager.getModelPath()
        assertTrue(path.endsWith(LlmConfig.DEFAULT_MODEL_NAME))
        assertTrue(path.startsWith(tempDir.absolutePath))
    }

    @Test
    fun `deleteModel removes the file`() {
        File(tempDir, LlmConfig.DEFAULT_MODEL_NAME).writeText("fake model")
        assertTrue(manager.isModelAvailable())

        val deleted = manager.deleteModel()
        assertTrue(deleted)
        assertFalse(manager.isModelAvailable())
    }

    @Test
    fun `deleteModel returns false when no model`() {
        assertFalse(manager.deleteModel())
    }

    @Test
    fun `getModelSizeBytes returns 0 when no model`() {
        assertEquals(0, manager.getModelSizeBytes())
    }

    @Test
    fun `getModelSizeBytes returns actual size`() {
        val content = "x".repeat(1024)
        File(tempDir, LlmConfig.DEFAULT_MODEL_NAME).writeText(content)
        assertEquals(1024, manager.getModelSizeBytes())
    }

    @Test
    fun `initial download state is Idle`() {
        assertEquals(DownloadState.Idle, manager.downloadState.value)
    }

    @Test
    fun `cancelDownload resets state to Idle`() {
        manager.cancelDownload()
        assertEquals(DownloadState.Idle, manager.downloadState.value)
    }

    @Test
    fun `models directory is created on init`() {
        val newDir = File(tempDir, "sub/models")
        assertFalse(newDir.exists())

        ModelManager(newDir)
        assertTrue(newDir.exists())
    }
}
