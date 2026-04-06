package com.novaterm.core.llm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Inference backend using Google LiteRT-LM (successor to MediaPipe LLM).
 *
 * Supports hardware acceleration via delegates:
 * - NPU: Qualcomm QNN (Snapdragon 8 Gen 3+), MediaTek NeuroPilot (Dimensity 9300+)
 * - GPU: OpenCL (most Android devices with 6GB+ RAM)
 * - CPU: XNNPACK (universal fallback, all Android 11+ devices)
 *
 * Model format: .litertlm (pre-packaged from HuggingFace) or .gguf (auto-detected).
 *
 * Backend selection in [GemmaEngine]:
 * - .litertlm → this backend (LiteRT-LM with hardware delegates)
 * - .gguf → [MediaPipeLlmBackend] (llama.cpp via MediaPipe, CPU-only)
 * - .tflite → [LiteRtBackend] (legacy, CPU-only)
 *
 * Loaded via reflection to avoid hard compile-time dependency on LiteRT-LM.
 * At runtime, the app module must provide `com.google.ai.edge.litertlm:litertlm-android`.
 *
 * @see <a href="https://github.com/google-ai-edge/LiteRT-LM">LiteRT-LM GitHub</a>
 */
class LiteRtLmBackend(
    private val context: Context,
    private val modelFile: File,
    private val maxTokens: Int = 512,
    private val temperature: Float = 0.3f,
    private val topK: Int = 10,
    private val topP: Float = 0.95f,
    private val accelerator: Accelerator = Accelerator.AUTO,
) : InferenceBackend {

    /**
     * Hardware accelerator preference.
     * [AUTO] tries NPU → GPU → CPU in order of availability.
     */
    enum class Accelerator {
        /** Auto-detect best available: NPU → GPU → CPU. */
        AUTO,
        /** Force NPU (fails if unavailable). */
        NPU,
        /** Force GPU via OpenCL (fails if unavailable). */
        GPU,
        /** CPU only via XNNPACK. */
        CPU,
    }

    private var engineHandle: Any? = null
    private var conversationHandle: Any? = null

    override fun load() {
        if (!modelFile.exists()) {
            throw RuntimeException("Model not found: ${modelFile.absolutePath}")
        }

        try {
            // Build EngineConfig
            val backendObj = resolveBackend()
            val engineConfigClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
            val engineConfigBuilderMethod = engineConfigClass.getMethod("builder")
            val configBuilder = engineConfigBuilderMethod.invoke(null)
            val configBuilderClass = configBuilder.javaClass

            configBuilderClass.getMethod("setModelPath", String::class.java)
                .invoke(configBuilder, modelFile.absolutePath)
            configBuilderClass.getMethod("setCacheDir", String::class.java)
                .invoke(configBuilder, context.cacheDir.absolutePath)

            // Set backend if resolved
            if (backendObj != null) {
                runCatching {
                    val backendBaseClass = Class.forName("com.google.ai.edge.litertlm.Backend")
                    configBuilderClass.getMethod("setBackend", backendBaseClass)
                        .invoke(configBuilder, backendObj)
                }
            }

            val engineConfig = configBuilderClass.getMethod("build").invoke(configBuilder)

            // Create Engine
            val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
            val engineConstructor = engineClass.getConstructor(engineConfigClass)
            val engine = engineConstructor.newInstance(engineConfig)

            // Initialize (blocking, may take 5-10s on NPU for AOT compile)
            engineClass.getMethod("initialize").invoke(engine)
            engineHandle = engine

            // Create ConversationConfig with sampling
            val samplerConfigClass = Class.forName("com.google.ai.edge.litertlm.SamplerConfig")
            val samplerBuilder = samplerConfigClass.getMethod("builder").invoke(null)
            val samplerBuilderClass = samplerBuilder.javaClass
            samplerBuilderClass.getMethod("setTopK", Int::class.java).invoke(samplerBuilder, topK)
            samplerBuilderClass.getMethod("setTopP", Float::class.java).invoke(samplerBuilder, topP)
            samplerBuilderClass.getMethod("setTemperature", Float::class.java).invoke(samplerBuilder, temperature)
            val samplerConfig = samplerBuilderClass.getMethod("build").invoke(samplerBuilder)

            val convConfigClass = Class.forName("com.google.ai.edge.litertlm.ConversationConfig")
            val convConfigBuilder = convConfigClass.getMethod("builder").invoke(null)
            val convConfigBuilderClass = convConfigBuilder.javaClass

            convConfigBuilderClass.getMethod("setSamplerConfig", samplerConfigClass)
                .invoke(convConfigBuilder, samplerConfig)

            val convConfig = convConfigBuilderClass.getMethod("build").invoke(convConfigBuilder)

            // createConversation
            conversationHandle = engineClass
                .getMethod("createConversation", convConfigClass)
                .invoke(engine, convConfig)

            Log.i(TAG, "LiteRT-LM engine loaded (${accelerator}): ${modelFile.name}")
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(
                "LiteRT-LM not found. Add com.google.ai.edge.litertlm:litertlm-android dependency.", e
            )
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException("LiteRT-LM API error: ${e.cause?.message ?: e.message}", e)
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        val conv = conversationHandle
            ?: throw IllegalStateException("Backend not loaded — call load() first")

        return try {
            // sendMessage (synchronous variant)
            val sendMethod = conv.javaClass.getMethod("sendMessage", String::class.java)
            sendMethod.invoke(conv, prompt) as? String ?: ""
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException("LiteRT-LM inference failed: ${e.cause?.message ?: e.message}", e)
        }
    }

    override fun close() {
        try {
            conversationHandle?.let { conv ->
                conv.javaClass.getMethod("close").invoke(conv)
            }
        } catch (e: Exception) {
            if (android.util.Log.isLoggable(TAG, android.util.Log.WARN)) {
                android.util.Log.w(TAG, "Failed to close conversation handle", e)
            }
        }
        try {
            engineHandle?.let { engine ->
                engine.javaClass.getMethod("close").invoke(engine)
            }
        } catch (e: Exception) {
            if (android.util.Log.isLoggable(TAG, android.util.Log.WARN)) {
                android.util.Log.w(TAG, "Failed to close engine handle", e)
            }
        }
        conversationHandle = null
        engineHandle = null
        Log.i(TAG, "LiteRT-LM engine released")
    }

    /**
     * Resolve the hardware backend via reflection.
     * Returns null if the requested accelerator is unavailable (falls back to default CPU).
     */
    private fun resolveBackend(): Any? {
        return when (accelerator) {
            Accelerator.CPU -> resolveCpuBackend()
            Accelerator.GPU -> resolveGpuBackend()
            Accelerator.NPU -> resolveNpuBackend()
            Accelerator.AUTO -> resolveAutoBackend()
        }
    }

    private fun resolveAutoBackend(): Any? {
        // Try NPU → GPU → CPU
        resolveNpuBackend()?.let {
            Log.i(TAG, "Auto-selected NPU backend")
            return it
        }
        resolveGpuBackend()?.let {
            Log.i(TAG, "Auto-selected GPU backend")
            return it
        }
        Log.i(TAG, "Falling back to CPU backend")
        return resolveCpuBackend()
    }

    private fun resolveNpuBackend(): Any? {
        return try {
            val npuClass = Class.forName("com.google.ai.edge.litertlm.Backend\$NPU")
            val constructor = npuClass.getConstructor(String::class.java)
            constructor.newInstance(context.applicationInfo.nativeLibraryDir)
        } catch (_: ClassNotFoundException) {
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "NPU backend not available (class not found)")
            null
        } catch (e: Exception) {
            Log.w(TAG, "NPU backend failed to initialize", e)
            null
        }
    }

    private fun resolveGpuBackend(): Any? {
        return try {
            val gpuClass = Class.forName("com.google.ai.edge.litertlm.Backend\$GPU")
            gpuClass.getDeclaredConstructor().newInstance()
        } catch (_: ClassNotFoundException) {
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "GPU backend not available (class not found)")
            null
        } catch (e: Exception) {
            Log.w(TAG, "GPU backend failed to initialize", e)
            null
        }
    }

    private fun resolveCpuBackend(): Any? {
        return try {
            val cpuClass = Class.forName("com.google.ai.edge.litertlm.Backend\$CPU")
            cpuClass.getDeclaredConstructor().newInstance()
        } catch (_: Exception) {
            null // Default is CPU anyway
        }
    }

    companion object {
        private const val TAG = "LiteRtLmBackend"
    }
}
