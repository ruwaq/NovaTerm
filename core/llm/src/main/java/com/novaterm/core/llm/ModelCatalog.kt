package com.novaterm.core.llm

/**
 * Catalog of on-device models for NovaTerm's AI features.
 *
 * Organized in tiers by capability and resource usage:
 * - Nano: <200MB RAM, basic suggestions (SmolLM2)
 * - Standard: <1GB RAM, good suggestions (Gemma 3 270M)
 * - Pro: <2GB RAM, strong reasoning + function calling (Gemma 4 E2B) ← DEFAULT
 * - Advanced: <4GB RAM, best quality (Gemma 4 E4B)
 *
 * All models are GGUF format from HuggingFace for llama.cpp compatibility.
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
        /** Model family for prompt format selection. */
        val family: ModelFamily = ModelFamily.GEMMA,
    )

    /** Model family determines prompt format and capabilities. */
    enum class ModelFamily {
        /** Gemma 3 and earlier: <start_of_turn> format. */
        GEMMA,
        /** Gemma 4: native system prompt, function calling, thinking. */
        GEMMA4,
        /** SmolLM: ChatML format. */
        SMOLLM,
        /** Qwen: ChatML format. */
        QWEN,
    }

    // ── Gemma 4 (recommended) ───────────────────────────────

    /** Default model — Gemma 4 E2B: best balance of quality and speed. */
    val DEFAULT = ModelInfo(
        id = "gemma-4-e2b-q4",
        displayName = "Gemma 4 E2B",
        description = "Google's latest. Reasoning, function calling, 128K context. Recommended.",
        url = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf",
        filename = "gemma-4-E2B-it-Q4_K_M.gguf",
        sizeMb = 1500,
        ramMb = 1800,
        isDefault = true,
        family = ModelFamily.GEMMA4,
    )

    /** All available models, sorted by size (smallest first). */
    val ALL = listOf(
        // ── Nano tier (<200MB RAM) ──
        ModelInfo(
            id = "smollm2-135m",
            displayName = "SmolLM2 135M",
            description = "Ultra-lightweight. Minimal RAM, basic suggestions.",
            url = "https://huggingface.co/QuantFactory/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct.Q4_K_M.gguf",
            filename = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
            sizeMb = 100,
            ramMb = 200,
            family = ModelFamily.SMOLLM,
        ),

        // ── Standard tier (<1GB RAM) ──
        ModelInfo(
            id = "gemma-3-270m-q4",
            displayName = "Gemma 3 270M",
            description = "Google's smallest Gemma. Fast, efficient.",
            url = "https://huggingface.co/ggml-org/gemma-3-270m-GGUF/resolve/main/gemma-3-270m-it-Q4_K_M.gguf",
            filename = "gemma-3-270m-it-Q4_K_M.gguf",
            sizeMb = 200,
            ramMb = 400,
            family = ModelFamily.GEMMA,
        ),
        ModelInfo(
            id = "qwen3-0.6b",
            displayName = "Qwen3 0.6B",
            description = "Alibaba's model. Good reasoning, moderate RAM.",
            url = "https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF/resolve/main/Qwen_Qwen3-0.6B-Q4_K_M.gguf",
            filename = "Qwen3-0.6B-Q4_K_M.gguf",
            sizeMb = 430,
            ramMb = 800,
            family = ModelFamily.QWEN,
        ),

        // ── Pro tier (<2GB RAM) — RECOMMENDED ──
        DEFAULT,

        // ── Advanced tier (<4GB RAM) ──
        ModelInfo(
            id = "gemma-4-e4b-q4",
            displayName = "Gemma 4 E4B",
            description = "Strongest on-device model. Best coding + reasoning. Needs 4GB+ free RAM.",
            url = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
            filename = "gemma-4-E4B-it-Q4_K_M.gguf",
            sizeMb = 2800,
            ramMb = 3500,
            family = ModelFamily.GEMMA4,
        ),
    )

    /** Find a model by ID. */
    fun findById(id: String): ModelInfo? = ALL.find { it.id == id }

    /** Get models that fit within a RAM budget (MB). */
    fun modelsForRam(availableRamMb: Int): List<ModelInfo> =
        ALL.filter { it.ramMb <= availableRamMb }
}
