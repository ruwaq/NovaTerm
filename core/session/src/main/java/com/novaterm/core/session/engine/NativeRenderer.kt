package com.novaterm.core.session.engine

import android.view.Surface

/**
 * JNI bridge to the Rust GPU renderer (wgpu Vulkan compute shaders).
 *
 * Lifecycle:
 *   nativeCreateGpu() → handle
 *   nativeAttachSurface(handle, surface, w, h)
 *   nativeRenderFrame(handle, sessionHandle) (per frame via Choreographer)
 *   nativeDetachSurface(handle) (Activity pause)
 *   nativeDestroyGpu(handle) (cleanup)
 *
 * Thread safety: all native methods are safe to call from any thread.
 * The Rust side uses parking_lot::Mutex for synchronization.
 */
internal object NativeRenderer {

    init {
        NativeTerminal.ensureLoaded() // Same .so contains all native APIs
    }

    /** Create GPU renderer (wgpu context + pipeline + atlas). Returns handle or -1. */
    external fun nativeCreateGpu(): Long

    /** Attach Android Surface for Vulkan presentation. */
    external fun nativeAttachSurface(handle: Long, surface: Surface, width: Int, height: Int)

    /** Detach surface (Activity going to background). GPU context survives. */
    external fun nativeDetachSurface(handle: Long)

    /** Render one frame from session's grid. Returns true if frame was presented. */
    external fun nativeRenderFrame(handle: Long, sessionHandle: Long): Boolean

    /** Resize surface. */
    external fun nativeResizeSurface(handle: Long, width: Int, height: Int)

    /** Destroy GPU renderer and all resources. */
    external fun nativeDestroyGpu(handle: Long)
}
