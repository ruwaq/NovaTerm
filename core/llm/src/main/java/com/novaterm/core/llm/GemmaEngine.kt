package com.novaterm.core.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * On-device Gemma inference engine.
 *
 * This is the concrete implementation of [LlmEngine] using Google's
 * Gemma model. Completely optional — only loaded when the user
 * explicitly enables it and has downloaded the model.
 *
 * The actual inference runtime (LiteRT, llama.cpp, etc.) is abstracted
 * behind [InferenceBackend]. This allows swapping backends without
 * changing the engine logic.
 *
 * Lifecycle:
 * 1. User enables in Settings → [initialize] called
 * 2. Backend loads model from disk
 * 3. [suggest]/[explain] available
 * 4. User disables or app destroyed → [release] called
 */
class GemmaEngine(private val config: LlmConfig) : LlmEngine {

    private val _state = MutableStateFlow(LlmState.IDLE)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    private var backend: InferenceBackend? = null

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (_state.value == LlmState.READY) return@withContext true

        _state.value = LlmState.LOADING

        if (!config.modelExists) {
            Log.w(TAG, "Model not found: ${config.modelPath}")
            _state.value = LlmState.MODEL_NOT_FOUND
            return@withContext false
        }

        try {
            val b = LiteRtBackend(File(config.modelPath), config.numThreads)
            b.load()
            backend = b
            _state.value = LlmState.READY
            Log.i(TAG, "Gemma model loaded: ${config.modelPath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _state.value = LlmState.ERROR
            false
        }
    }

    override suspend fun suggest(context: TerminalContext): LlmSuggestion? {
        if (_state.value != LlmState.READY) return null
        val b = backend ?: return null

        _state.value = LlmState.INFERRING
        try {
            val prompt = PromptBuilder.buildSuggestionPrompt(context)
            val response = withTimeoutOrNull(config.inferenceTimeoutMs) {
                b.generate(prompt, config.maxTokens, config.temperature)
            }
            _state.value = LlmState.READY

            if (response == null) {
                Log.w(TAG, "Inference timed out")
                return null
            }

            val command = PromptBuilder.parseCommandResponse(response) ?: return null
            return LlmSuggestion(
                command = command,
                confidence = 0.85,
                reasoning = null,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            _state.value = LlmState.READY
            return null
        }
    }

    override suspend fun explain(input: String, context: TerminalContext): String? {
        if (_state.value != LlmState.READY) return null
        val b = backend ?: return null

        _state.value = LlmState.INFERRING
        try {
            val prompt = PromptBuilder.buildExplanationPrompt(input, context)
            val response = withTimeoutOrNull(config.inferenceTimeoutMs) {
                b.generate(prompt, config.maxTokens * 2, config.temperature)
            }
            _state.value = LlmState.READY
            return response?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Explain inference failed", e)
            _state.value = LlmState.READY
            return null
        }
    }

    override fun release() {
        try {
            backend?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing backend", e)
        }
        backend = null
        _state.value = LlmState.IDLE
        Log.i(TAG, "Gemma model released")
    }

    companion object {
        private const val TAG = "GemmaEngine"
    }
}
