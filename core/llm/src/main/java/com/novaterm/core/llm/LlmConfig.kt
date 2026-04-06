package com.novaterm.core.llm

import java.io.File

/**
 * Configuration for the on-device LLM.
 *
 * All paths and parameters are user-configurable via Settings.
 * The model is never bundled in the APK — always downloaded separately.
 */
data class LlmConfig(
    /** Path to the model file on disk. */
    val modelPath: String = "",
    /** Sampling temperature (0.0 = deterministic, 1.0 = creative). */
    val temperature: Float = 0.3f,
    /** Maximum tokens to generate per inference. */
    val maxTokens: Int = 64,
    /** Number of recent commands to include in context. */
    val contextDepth: Int = 10,
    /** Maximum characters of last command output to include. */
    val maxOutputChars: Int = 500,
    /** Timeout for a single inference call (ms). 2s keeps UI responsive on mobile. */
    val inferenceTimeoutMs: Long = 2_000,
    /** Number of CPU threads for inference. */
    val numThreads: Int = 4,
    /** Model family for prompt format selection. */
    val modelFamily: ModelCatalog.ModelFamily = ModelCatalog.ModelFamily.GEMMA4,
) {
    /** Whether a model file exists at the configured path. */
    val modelExists: Boolean get() = modelPath.isNotBlank() && File(modelPath).exists()

    companion object {
        /** Default model filename (user downloads this). */
        const val DEFAULT_MODEL_NAME = "gemma-4-E2B-it-Q4_K_M.gguf"

        /** Approximate model size for download UI (mixed 2/4/8-bit quantization). */
        const val MODEL_SIZE_MB = 2583

        /** Approximate RAM usage when loaded (CPU backend). */
        const val MODEL_RAM_MB = 1733

        /** Model download URL — GGUF Q4_K_M from Unsloth. */
        const val MODEL_URL = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf"
    }
}
