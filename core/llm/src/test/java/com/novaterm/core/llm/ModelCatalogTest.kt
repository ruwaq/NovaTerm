package com.novaterm.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `default model is Gemma 4 E2B`() {
        assertEquals("gemma-4-e2b-q4", ModelCatalog.DEFAULT.id)
        assertTrue(ModelCatalog.DEFAULT.displayName.contains("Gemma 4"))
        assertEquals(ModelCatalog.ModelFamily.GEMMA4, ModelCatalog.DEFAULT.family)
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
    fun `all models have valid extension`() {
        val validExtensions = listOf(".gguf", ".litertlm", ".tflite")
        ModelCatalog.ALL.forEach { model ->
            assertTrue(
                "${model.id} filename should have a valid model extension",
                validExtensions.any { model.filename.endsWith(it) },
            )
        }
    }

    @Test
    fun `model format matches file extension`() {
        ModelCatalog.ALL.forEach { model ->
            when (model.format) {
                ModelCatalog.ModelFormat.GGUF ->
                    assertTrue("${model.id} GGUF format should have .gguf extension",
                        model.filename.endsWith(".gguf"))
                ModelCatalog.ModelFormat.LITERT_LM ->
                    assertTrue("${model.id} LiteRT-LM format should have .litertlm extension",
                        model.filename.endsWith(".litertlm"))
            }
        }
    }

    @Test
    fun `all models are viable for mobile`() {
        ModelCatalog.ALL.forEach { model ->
            assertTrue("${model.id} should be under 4GB", model.sizeMb < 4000)
            assertTrue("${model.id} RAM should be under 5GB", model.ramMb < 5000)
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

    @Test
    fun `Gemma 4 models have GEMMA4 family`() {
        ModelCatalog.ALL.filter { it.id.contains("gemma-4") }.forEach { model ->
            assertEquals(
                "${model.id} should be GEMMA4 family",
                ModelCatalog.ModelFamily.GEMMA4,
                model.family,
            )
        }
    }

    @Test
    fun `modelsForRam filters correctly`() {
        val smallRam = ModelCatalog.modelsForRam(500)
        assertTrue(smallRam.all { it.ramMb <= 500 })
        assertTrue(smallRam.isNotEmpty())

        val bigRam = ModelCatalog.modelsForRam(5000)
        assertEquals(ModelCatalog.ALL.size, bigRam.size)
    }

    @Test
    fun `findById returns correct model`() {
        val model = ModelCatalog.findById("gemma-4-e2b-q4")
        assertNotNull(model)
        assertEquals("Gemma 4 E2B", model!!.displayName)
    }
}
