package com.novaterm.core.llm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Inference backend using Google MediaPipe Tasks GenAI (llama.cpp under the hood).
 *
 * Supports GGUF model files — the standard format for quantized LLMs.
 * MediaPipe handles tokenization, sampling, and KV-cache internally.
 *
 * Loaded via reflection to avoid hard compile-time dependency in core:llm.
 * The app module provides the actual runtime via `mediapipe-tasks-genai`.
 *
 * MediaPipe API:
 *   com.google.mediapipe.tasks.genai.llminference.LlmInference
 *   com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
 */
class MediaPipeLlmBackend(
    private val context: Context,
    private val modelFile: File,
    private val maxTokens: Int = 512,
    private val temperature: Float = 0.3f,
    private val numThreads: Int = 4,
) : InferenceBackend {

    private var llmHandle: Any? = null

    override fun load() {
        if (!modelFile.exists()) {
            throw RuntimeException("GGUF model not found: ${modelFile.absolutePath}")
        }

        try {
            // LlmInference.LlmInferenceOptions.builder()
            val optionsClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions"
            )
            val builderMethod = optionsClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            val builderClass = builder.javaClass

            builderClass.getMethod("setModelPath", String::class.java)
                .invoke(builder, modelFile.absolutePath)
            builderClass.getMethod("setMaxTokens", Int::class.java)
                .invoke(builder, maxTokens)
            builderClass.getMethod("setMaxNumImages", Int::class.java)
                .invoke(builder, 0)

            // Temperature: set via setTopK/setTemperature depending on API version
            runCatching {
                builderClass.getMethod("setTemperature", Float::class.java)
                    .invoke(builder, temperature)
            }
            runCatching {
                builderClass.getMethod("setNumThreads", Int::class.java)
                    .invoke(builder, numThreads)
            }

            val options = builderClass.getMethod("build").invoke(builder)

            // LlmInference.createFromOptions(context, options)
            val inferenceClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference"
            )
            val createMethod = inferenceClass.getMethod(
                "createFromOptions",
                Context::class.java,
                optionsClass,
            )
            llmHandle = createMethod.invoke(null, context, options)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(
                "MediaPipe tasks-genai not found. Add mediapipe-tasks-genai dependency.", e
            )
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException("MediaPipe API incompatible: ${e.message}", e)
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        val handle = llmHandle
            ?: throw IllegalStateException("Backend not loaded — call load() first")

        return try {
            // LlmInference.generateResponse(prompt)
            val generateMethod = handle.javaClass.getMethod("generateResponse", String::class.java)
            generateMethod.invoke(handle, prompt) as? String ?: ""
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException("MediaPipe inference failed: ${e.message}", e)
        }
    }

    override fun close() {
        try {
            llmHandle?.let { handle ->
                handle.javaClass.getMethod("close").invoke(handle)
            }
        } catch (e: Exception) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Failed to close LlmInference handle", e)
            }
        }
        llmHandle = null
    }

    companion object {
        private const val TAG = "MediaPipeLlmBackend"
    }
}
