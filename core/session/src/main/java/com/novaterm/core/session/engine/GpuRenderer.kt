package com.novaterm.core.session.engine

import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicLong

/**
 * GPU information from the Vulkan adapter.
 * Parsed from the pipe-delimited string returned by native code.
 */
data class GpuInfo(
    val name: String,
    val vendor: String,
    val driver: String,
    val backend: String,
    val deviceType: String,
    val maxTexture2d: Int,
    val maxStorageBuffer: Int,
    val maxWorkgroupInvocations: Int,
) {
    /** One-line summary for logging. */
    fun summary(): String =
        "$name ($vendor) [$backend] max_tex=${maxTexture2d}px max_ssbo=${maxStorageBuffer / 1024}KB"

    companion object {
        /** Parse from pipe-delimited native string. */
        internal fun parse(raw: String): GpuInfo? {
            val parts = raw.split("|")
            if (parts.size < 8) return null
            return GpuInfo(
                name = parts[0],
                vendor = parts[1],
                driver = parts[2],
                backend = parts[3],
                deviceType = parts[4],
                maxTexture2d = parts[5].toIntOrNull() ?: 0,
                maxStorageBuffer = parts[6].toIntOrNull() ?: 0,
                maxWorkgroupInvocations = parts[7].toIntOrNull() ?: 0,
            )
        }
    }
}

/**
 * Public wrapper around the Rust GPU renderer ([NativeRenderer]).
 *
 * Manages the lifecycle of a wgpu Vulkan context: create → attach surface →
 * render frames → detach → destroy. The GPU context survives Activity
 * pause/resume; only the surface is detached.
 *
 * Multi-GPU compatible: works with Adreno, Mali, PowerVR, Xclipse, and
 * software renderers. Detects GPU capabilities and adapts automatically.
 * Signals when software fallback is recommended via [shouldFallback].
 *
 * Thread safety: all methods are safe to call from any thread.
 * The Rust side uses parking_lot::Mutex for synchronization.
 */
class GpuRenderer private constructor(handle: Long) {

    private val handleRef = AtomicLong(handle)

    /** Cached GPU info (queried once on creation). */
    val gpuInfo: GpuInfo? = queryGpuInfo(handle)

    private fun validHandle(): Long {
        val h = handleRef.get()
        return if (h > 0) h else -1
    }

    /** Attach an Android Surface for Vulkan presentation. */
    fun attachSurface(surface: Surface, width: Int, height: Int) {
        val h = validHandle()
        if (h < 0) return
        NativeRenderer.nativeAttachSurface(h, surface, width, height)
    }

    /** Detach surface (Activity going to background). GPU context survives. */
    fun detachSurface() {
        val h = validHandle()
        if (h < 0) return
        NativeRenderer.nativeDetachSurface(h)
    }

    /**
     * Render one frame from a session's grid.
     *
     * @param sessionEngine The Rust session engine providing grid data.
     * @return true if a frame was presented to the surface.
     */
    fun renderFrame(sessionEngine: RustSessionEngine): Boolean {
        val h = validHandle()
        if (h < 0) return false
        val sessionHandle = sessionEngine.nativeHandle()
        if (sessionHandle <= 0) return false
        return NativeRenderer.nativeRenderFrame(h, sessionHandle)
    }

    /** Resize the presentation surface. */
    fun resizeSurface(width: Int, height: Int) {
        val h = validHandle()
        if (h < 0) return
        NativeRenderer.nativeResizeSurface(h, width, height)
    }

    /**
     * Check if the GPU renderer recommends falling back to software rendering.
     * This is true when too many consecutive render errors have occurred,
     * indicating the GPU driver is unstable or unsupported.
     */
    fun shouldFallback(): Boolean {
        val h = validHandle()
        if (h < 0) return true
        return NativeRenderer.nativeShouldFallback(h)
    }

    /** Destroy the GPU renderer and all resources. */
    fun destroy() {
        val h = handleRef.getAndSet(-1)
        if (h <= 0) return
        NativeRenderer.nativeDestroyGpu(h)
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "GpuRenderer destroyed")
    }

    companion object {
        private const val TAG = "GpuRenderer"

        /**
         * Create a new GPU renderer (wgpu context + pipeline + glyph atlas).
         * Adapts to the detected GPU vendor and capabilities.
         *
         * @return GpuRenderer instance, or null if GPU is not available.
         */
        fun create(): GpuRenderer? {
            return try {
                NativeTerminal.ensureLoaded()
                val handle = NativeRenderer.nativeCreateGpu()
                if (handle <= 0) {
                    Log.e(TAG, "GPU renderer creation failed (no Vulkan support?)")
                    return null
                }
                val renderer = GpuRenderer(handle)
                Log.i(TAG, "GPU renderer ready: ${renderer.gpuInfo?.summary() ?: "unknown GPU"}")
                renderer
            } catch (e: UnsatisfiedLinkError) {
                // libnovaterm.so was compiled without GPU feature
                Log.w(TAG, "GPU native methods not available: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e(TAG, "GPU renderer creation error", e)
                null
            }
        }

        /** Query GPU info from native renderer. */
        private fun queryGpuInfo(handle: Long): GpuInfo? {
            if (handle <= 0) return null
            return try {
                NativeRenderer.nativeGetGpuInfo(handle)?.let { GpuInfo.parse(it) }
            } catch (e: Exception) {
                null
            }
        }
    }
}
