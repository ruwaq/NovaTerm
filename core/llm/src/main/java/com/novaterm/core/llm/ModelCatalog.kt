package com.novaterm.core.llm

/**
 * Catalog of on-device models that work well on Android phones.
 *
 * Only models proven to run on mobile hardware (<1GB RAM) are listed.
 * The default (Gemma 270M) works on any phone with 4GB+ RAM.
 * Users can also load any custom GGUF model from storage.
 */
object ModelCatalog {

    /** A model available for download. */
    data class ModelInfo(
        /** Unique identifier. */
        val id: String,
        /** Display name for the UI. */
        val displayName: String,
        /** Short description. */
        val description: String,
        /** Download URL (HuggingFace direct link). */
        val url: String,
        /** Filename on disk. */
        val filename: String,
        /** Approximate download size in MB. */
        val sizeMb: Int,
        /** Approximate RAM usage in MB. */
        val ramMb: Int,
        /** Whether this is the recommended default. */
        val isDefault: Boolean = false,
    )

    /** Default model — smallest, fastest, works on any phone. */
    val DEFAULT = ModelInfo(
        id = "gemma-3-270m-q4",
        displayName = "Gemma 3 270M",
        description = "Google's smallest model. Fast, efficient, ideal for command suggestions.",
        url = "https://huggingface.co/ggml-org/gemma-3-270m-GGUF/resolve/main/gemma-3-270m-it-Q4_K_M.gguf",
        filename = "gemma-3-270m-it-Q4_K_M.gguf",
        sizeMb = 200,
        ramMb = 400,
        isDefault = true,
    )

    /** All available models, sorted by size (smallest first). */
    val ALL = listOf(
        ModelInfo(
            id = "smollm2-135m",
            displayName = "SmolLM2 135M",
            description = "Ultra-lightweight. Minimal RAM, basic suggestions.",
            url = "https://huggingface.co/QuantFactory/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct.Q4_K_M.gguf",
            filename = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
            sizeMb = 100,
            ramMb = 200,
        ),
        DEFAULT,
        ModelInfo(
            id = "gemma-3-270m-q8",
            displayName = "Gemma 3 270M (Q8)",
            description = "Higher quality, slightly larger. Better suggestions.",
            url = "https://huggingface.co/ggml-org/gemma-3-270m-GGUF/resolve/main/gemma-3-270m-it-Q8_0.gguf",
            filename = "gemma-3-270m-it-Q8_0.gguf",
            sizeMb = 290,
            ramMb = 550,
        ),
        ModelInfo(
            id = "qwen3-0.6b",
            displayName = "Qwen3 0.6B",
            description = "Alibaba's model. Stronger reasoning, needs more RAM.",
            url = "https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF/resolve/main/Qwen_Qwen3-0.6B-Q4_K_M.gguf",
            filename = "Qwen3-0.6B-Q4_K_M.gguf",
            sizeMb = 430,
            ramMb = 800,
        ),
    )

    /** Find a model by ID. */
    fun findById(id: String): ModelInfo? = ALL.find { it.id == id }
}
