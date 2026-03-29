package com.novaterm.core.session.engine

import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicLong

/**
 * Public wrapper around the Rust GPU renderer ([NativeRenderer]).
 *
 * Manages the lifecycle of a wgpu Vulkan context: create → attach surface →
 * render frames → detach → destroy. The GPU context survives Activity
 * pause/resume; only the surface is detached.
 *
 * Thread safety: all methods are safe to call from any thread.
 * The Rust side uses parking_lot::Mutex for synchronization.
 */
class GpuRenderer private constructor(handle: Long) {

    private val handleRef = AtomicLong(handle)

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
        // Access the session handle via reflection-free companion method
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

    /** Destroy the GPU renderer and all resources. */
    fun destroy() {
        val h = handleRef.getAndSet(-1)
        if (h <= 0) return
        NativeRenderer.nativeDestroyGpu(h)
        Log.d(TAG, "GpuRenderer destroyed")
    }

    companion object {
        private const val TAG = "GpuRenderer"

        /**
         * Create a new GPU renderer (wgpu context + pipeline + glyph atlas).
         * @return GpuRenderer instance, or null if creation failed.
         */
        fun create(): GpuRenderer? {
            NativeTerminal.ensureLoaded()
            val handle = NativeRenderer.nativeCreateGpu()
            if (handle <= 0) {
                Log.e(TAG, "Failed to create GPU renderer")
                return null
            }
            Log.i(TAG, "GpuRenderer created")
            return GpuRenderer(handle)
        }
    }
}
