package com.novaterm.core.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * On-device Gemma inference engine.
 *
 * Thread-safe: uses a [Mutex] to serialize inference calls
 * and protect state transitions from concurrent access.
 *
 * Lifecycle:
 * 1. User enables in Settings → [initialize] called
 * 2. Backend loads model from disk
 * 3. [suggest]/[explain] available (serialized via mutex)
 * 4. User disables or app destroyed → [release] called
 */
class GemmaEngine(private val config: LlmConfig) : LlmEngine {

    private val _state = MutableStateFlow(LlmState.IDLE)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    /** Serializes inference calls — backend is NOT thread-safe. */
    private val inferenceMutex = Mutex()

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
            val b = LiteRtBackend(java.io.File(config.modelPath), config.numThreads)
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

        return inferenceMutex.withLock {
            val b = backend ?: return@withLock null
            if (_state.value != LlmState.READY) return@withLock null

            _state.value = LlmState.INFERRING
            try {
                val prompt = PromptBuilder.buildSuggestionPrompt(context, config.modelFamily)
                val response = withTimeoutOrNull(config.inferenceTimeoutMs) {
                    b.generate(prompt, config.maxTokens, config.temperature)
                }
                _state.value = LlmState.READY

                if (response == null) {
                    Log.w(TAG, "Inference timed out")
                    return@withLock null
                }

                val command = PromptBuilder.parseCommandResponse(response) ?: return@withLock null
                LlmSuggestion(command = command, confidence = 0.85, reasoning = null)
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed", e)
                _state.value = LlmState.READY
                null
            }
        }
    }

    override suspend fun explain(input: String, context: TerminalContext): String? {
        if (_state.value != LlmState.READY) return null

        return inferenceMutex.withLock {
            val b = backend ?: return@withLock null
            if (_state.value != LlmState.READY) return@withLock null

            _state.value = LlmState.INFERRING
            try {
                val prompt = PromptBuilder.buildExplanationPrompt(input, context, config.modelFamily)
                val response = withTimeoutOrNull(config.inferenceTimeoutMs) {
                    b.generate(prompt, config.maxTokens * 2, config.temperature)
                }
                _state.value = LlmState.READY
                response?.trim()
            } catch (e: Exception) {
                Log.e(TAG, "Explain inference failed", e)
                _state.value = LlmState.READY
                null
            }
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
