package com.novaterm.feature.terminal.ui.screen

import android.util.Log
import android.view.Choreographer
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.novaterm.core.session.engine.GpuRenderer
import com.novaterm.core.session.engine.RustSessionEngine
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "GpuTerminalScreen"

/**
 * GPU-accelerated terminal screen using wgpu Vulkan compute shaders.
 *
 * Replaces the Java Canvas TerminalView with direct GPU rendering.
 * Uses AndroidExternalSurface for optimal compositor bypass.
 * Frame loop driven by Choreographer for VSync alignment.
 *
 * Multi-GPU compatible: automatically detects GPU vendor (Adreno, Mali,
 * PowerVR, Xclipse) and adapts rendering. Falls back to [onGpuUnavailable]
 * if GPU initialization fails or render errors exceed threshold.
 *
 * @param sessionEngine The active Rust session providing grid data.
 * @param onGpuUnavailable Called when GPU rendering fails and software
 *        fallback should be used. The caller should switch to TerminalView.
 * @param modifier Compose modifier for layout.
 */
@Composable
fun GpuTerminalScreen(
    sessionEngine: RustSessionEngine,
    onGpuUnavailable: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rendererState = remember { mutableStateOf<GpuRenderer?>(null) }
    val fallbackTriggered = remember { mutableStateOf(false) }

    // Create GPU renderer on first composition
    DisposableEffect(Unit) {
        val renderer = GpuRenderer.create()
        if (renderer == null) {
            Log.w(TAG, "GPU not available, requesting fallback")
            if (!fallbackTriggered.value) {
                fallbackTriggered.value = true
                onGpuUnavailable()
            }
        } else {
            renderer.gpuInfo?.let { info ->
                Log.i(TAG, "GPU: ${info.summary()}")
            }
        }
        rendererState.value = renderer

        onDispose {
            rendererState.value?.destroy()
            rendererState.value = null
        }
    }

    val renderer = rendererState.value ?: return

    AndroidExternalSurface(
        modifier = modifier.fillMaxSize(),
    ) {
        onSurface { surface, width, height ->
            renderer.attachSurface(surface, width, height)

            // Start frame loop via Choreographer
            val choreographer = Choreographer.getInstance()
            val running = AtomicBoolean(true)

            val frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!running.get()) return

                    // Check if GPU suggests fallback (too many errors)
                    if (renderer.shouldFallback()) {
                        Log.e(TAG, "GPU renderer error threshold exceeded, falling back")
                        running.set(false)
                        if (!fallbackTriggered.value) {
                            fallbackTriggered.value = true
                            onGpuUnavailable()
                        }
                        return
                    }

                    // Process pending PTY output then render via GPU
                    sessionEngine.processPending()
                    renderer.renderFrame(sessionEngine)
                    choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(frameCallback)

            surface.onChanged { newWidth, newHeight ->
                renderer.resizeSurface(newWidth, newHeight)
            }

            surface.onDestroyed {
                running.set(false)
                choreographer.removeFrameCallback(frameCallback)
                renderer.detachSurface()
            }
        }
    }
}
