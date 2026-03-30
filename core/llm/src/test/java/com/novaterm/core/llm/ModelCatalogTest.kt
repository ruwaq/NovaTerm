package com.novaterm.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `default model is Gemma 270M`() {
        assertEquals("gemma-3-270m-q4", ModelCatalog.DEFAULT.id)
        assertTrue(ModelCatalog.DEFAULT.displayName.contains("Gemma"))
    }

    @Test
    fun `all model IDs are unique`() {
        val ids = ModelCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `all model filenames are unique`() {
        val filenames = ModelCatalog.ALL.map { it.filename }
        assertEquals(filenames.size, filenames.distinct().size)
    }

    @Test
    fun `all models have GGUF extension`() {
        ModelCatalog.ALL.forEach { model ->
            assertTrue(
                "${model.id} filename should end with .gguf",
                model.filename.endsWith(".gguf"),
            )
        }
    }

    @Test
    fun `all model sizes are realistic for mobile`() {
        ModelCatalog.ALL.forEach { model ->
            assertTrue("${model.id} should be under 1GB", model.sizeMb < 1000)
            assertTrue("${model.id} RAM should be under 2GB", model.ramMb < 2000)
        }
    }

    @Test
    fun `exactly one model is default`() {
        val defaults = ModelCatalog.ALL.filter { it.isDefault }
        assertEquals(1, defaults.size)
    }

    @Test
    fun `SmolLM2 is smallest option`() {
        val smallest = ModelCatalog.ALL.minByOrNull { it.sizeMb }
        assertNotNull(smallest)
        assertTrue(smallest!!.id.contains("smollm"))
    }
}
