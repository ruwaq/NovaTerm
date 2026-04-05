package com.novaterm.core.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * On-device LLM inference engine supporting GGUF models via MediaPipe.
 *
 * Thread-safe: uses a [Mutex] to serialize inference calls
 * and protect state transitions from concurrent access.
 *
 * Backend selection:
 * - GGUF models (.gguf) → [MediaPipeLlmBackend] (llama.cpp via MediaPipe)
 * - TFLite models (.tflite) → [LiteRtBackend] (LiteRT interpreter)
 *
 * Lifecycle:
 * 1. User enables in Settings → [initialize] called
 * 2. Backend loads model from disk
 * 3. [suggest]/[explain] available (serialized via mutex)
 * 4. User disables or app destroyed → [release] called
 */
class GemmaEngine(
    private val config: LlmConfig,
    private val context: Context? = null,
) : LlmEngine {

    private val _state = MutableStateFlow(LlmState.IDLE)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    /** Serializes inference calls — backend is NOT thread-safe. */
    private val inferenceMutex = Mutex()

    private var backend: InferenceBackend? = null

    /** Guards against concurrent initialization. */
    private val initMutex = Mutex()

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (_state.value == LlmState.READY) return@withContext true

            _state.value = LlmState.LOADING

            if (!config.modelExists) {
                Log.w(TAG, "Model not found: ${config.modelPath}")
                _state.value = LlmState.MODEL_NOT_FOUND
                return@withContext false
            }

            try {
                val modelFile = File(config.modelPath)
                val b: InferenceBackend = when {
                    // GGUF → MediaPipe (llama.cpp). Requires context from app module.
                    config.modelPath.endsWith(".gguf", ignoreCase = true) && context != null ->
                        MediaPipeLlmBackend(
                            context = context,
                            modelFile = modelFile,
                            maxTokens = config.maxTokens,
                            temperature = config.temperature,
                            numThreads = config.numThreads,
                        )
                    // TFLite → LiteRT (legacy / fallback)
                    else ->
                        LiteRtBackend(modelFile, config.numThreads)
                }
                b.load()
                backend = b
                _state.value = LlmState.READY
                Log.i(TAG, "LLM model loaded (${b.javaClass.simpleName}): ${config.modelPath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                _state.value = LlmState.ERROR
                false
            }
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
