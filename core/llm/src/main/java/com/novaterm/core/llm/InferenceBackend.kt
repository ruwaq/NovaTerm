package com.novaterm.core.llm

import java.io.Closeable
import java.io.File

/**
 * Abstraction over the inference runtime.
 *
 * Allows swapping between LiteRT, llama.cpp, or other backends
 * without changing the engine logic. Each backend handles:
 * - Model loading and memory management
 * - Tokenization
 * - Text generation
 */
interface InferenceBackend : Closeable {
    /** Load the model into memory. */
    fun load()

    /**
     * Generate text from a prompt.
     *
     * @param prompt The input prompt (pre-formatted for the model).
     * @param maxTokens Maximum tokens to generate.
     * @param temperature Sampling temperature.
     * @return Generated text.
     */
    suspend fun generate(prompt: String, maxTokens: Int, temperature: Float): String
}

/**
 * LiteRT (TensorFlow Lite) inference backend.
 *
 * Uses Google's LiteRT runtime for on-device Gemma inference.
 * The Interpreter API loads .tflite models and runs tensor operations.
 */
class LiteRtBackend(
    private val modelFile: File,
    private val numThreads: Int = 4,
) : InferenceBackend {

    // LiteRT Interpreter loaded lazily
    private var interpreterHandle: Any? = null

    override fun load() {
        // Load via reflection to avoid hard compile-time dependency.
        // This allows the app to compile even if LiteRT is not resolved at build time
        // (e.g., on ARM64 Termux where Maven may not resolve all transitive deps).
        //
        // At runtime, LiteRT classes must be present for inference to work.
        // If not present, initialize() catches the exception and sets state = ERROR.
        try {
            val optionsClass = Class.forName("com.google.ai.edge.litert.InterpreterApi\$Options")
            val builderMethod = optionsClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            val setThreads = builder.javaClass.getMethod("setNumThreads", Int::class.java)
            setThreads.invoke(builder, numThreads)
            val buildMethod = builder.javaClass.getMethod("build")
            val options = buildMethod.invoke(builder)

            val interpreterClass = Class.forName("com.google.ai.edge.litert.InterpreterApi")
            val createMethod = interpreterClass.getMethod("create", File::class.java, optionsClass)
            interpreterHandle = createMethod.invoke(null, modelFile, options)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("LiteRT runtime not found. Add litert dependency.", e)
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        val interp = interpreterHandle
            ?: throw IllegalStateException("Backend not loaded")

        // Encode prompt
        val inputBytes = prompt.toByteArray(Charsets.UTF_8)
        val inputBuffer = java.nio.ByteBuffer.allocateDirect(inputBytes.size)
            .order(java.nio.ByteOrder.nativeOrder())
        inputBuffer.put(inputBytes)
        inputBuffer.rewind()

        // Allocate output
        val outputBuffer = java.nio.ByteBuffer.allocateDirect(maxTokens * 4)
            .order(java.nio.ByteOrder.nativeOrder())

        // Run inference
        val runMethod = interp.javaClass.getMethod(
            "run",
            Any::class.java,
            Any::class.java,
        )
        runMethod.invoke(interp, inputBuffer, outputBuffer)

        // Decode output
        outputBuffer.rewind()
        val outputBytes = ByteArray(outputBuffer.remaining())
        outputBuffer.get(outputBytes)
        return String(outputBytes, Charsets.UTF_8).trimEnd('\u0000')
    }

    override fun close() {
        try {
            interpreterHandle?.let { interp ->
                val closeMethod = interp.javaClass.getMethod("close")
                closeMethod.invoke(interp)
            }
        } catch (_: Exception) {
            // Best effort cleanup
        }
        interpreterHandle = null
    }
}
