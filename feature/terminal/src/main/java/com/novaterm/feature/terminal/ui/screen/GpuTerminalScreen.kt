package com.novaterm.feature.terminal.ui.screen

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

/**
 * GPU-accelerated terminal screen using wgpu Vulkan compute shaders.
 *
 * Replaces the Java Canvas TerminalView with direct GPU rendering.
 * Uses AndroidExternalSurface for optimal compositor bypass.
 * Frame loop driven by Choreographer for VSync alignment.
 *
 * @param sessionEngine The active Rust session providing grid data.
 * @param modifier Compose modifier for layout.
 */
@Composable
fun GpuTerminalScreen(
    sessionEngine: RustSessionEngine,
    modifier: Modifier = Modifier,
) {
    val rendererState = remember { mutableStateOf<GpuRenderer?>(null) }

    // Create GPU renderer on first composition
    DisposableEffect(Unit) {
        val renderer = GpuRenderer.create()
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
            var running = true

            val frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!running) return
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
                running = false
                choreographer.removeFrameCallback(frameCallback)
                renderer.detachSurface()
            }
        }
    }
}
