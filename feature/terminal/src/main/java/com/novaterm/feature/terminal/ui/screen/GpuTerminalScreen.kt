package com.novaterm.feature.terminal.ui.screen

import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import com.novaterm.core.session.engine.GpuRenderer
import com.novaterm.terminal.TerminalSession
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
 * @param sessionHandle The Rust native handle for the session providing grid data.
 * @param session The TerminalSession for sending keyboard input to the PTY.
 * @param onGpuUnavailable Called when GPU rendering fails and software
 *        fallback should be used. The caller should switch to TerminalView.
 * @param modifier Compose modifier for layout.
 */
@Composable
fun GpuTerminalScreen(
    sessionHandle: Long,
    session: TerminalSession,
    onGpuUnavailable: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rendererState = remember { mutableStateOf<GpuRenderer?>(null) }
    val fallbackTriggered = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

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
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val bytes = keyEventToBytes(keyEvent.nativeKeyEvent)
                    if (bytes != null) {
                        session.write(bytes, 0, bytes.size)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            },
    ) {
        onSurface { surface, width, height ->
            renderer.attachSurface(surface, width, height)
            focusRequester.requestFocus()

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

                    // Render frame from the Rust grid
                    renderer.renderFrame(sessionHandle)
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

/**
 * Convert an Android KeyEvent to terminal bytes.
 * Basic mapping for common keys.
 */
private fun keyEventToBytes(event: KeyEvent): ByteArray? {
    return when (event.keyCode) {
        KeyEvent.KEYCODE_ENTER -> byteArrayOf(0x0D)
        KeyEvent.KEYCODE_DEL -> byteArrayOf(0x7F)
        KeyEvent.KEYCODE_FORWARD_DEL -> byteArrayOf(0x1B.toByte(), 0x5B, 0x33, 0x7E)
        KeyEvent.KEYCODE_TAB -> byteArrayOf(0x09)
        KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(0x1B.toByte())
        KeyEvent.KEYCODE_DPAD_UP -> byteArrayOf(0x1B.toByte(), 0x5B, 0x41)
        KeyEvent.KEYCODE_DPAD_DOWN -> byteArrayOf(0x1B.toByte(), 0x5B, 0x42)
        KeyEvent.KEYCODE_DPAD_RIGHT -> byteArrayOf(0x1B.toByte(), 0x5B, 0x43)
        KeyEvent.KEYCODE_DPAD_LEFT -> byteArrayOf(0x1B.toByte(), 0x5B, 0x44)
        KeyEvent.KEYCODE_MOVE_HOME -> byteArrayOf(0x1B.toByte(), 0x5B, 0x48)
        KeyEvent.KEYCODE_MOVE_END -> byteArrayOf(0x1B.toByte(), 0x5B, 0x46)
        KeyEvent.KEYCODE_PAGE_UP -> byteArrayOf(0x1B.toByte(), 0x5B, 0x35, 0x7E)
        KeyEvent.KEYCODE_PAGE_DOWN -> byteArrayOf(0x1B.toByte(), 0x5B, 0x36, 0x7E)
        else -> {
            val c = event.unicodeChar
            if (c != 0) {
                val bytes = c.toString().toByteArray(Charsets.UTF_8)
                // Apply CTRL modifier
                if (event.isCtrlPressed && c in 'a'.code..'z'.code) {
                    byteArrayOf((c - 'a'.code + 1).toByte())
                } else {
                    bytes
                }
            } else {
                null
            }
        }
    }
}
