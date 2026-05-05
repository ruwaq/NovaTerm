package com.novaterm.feature.terminal.ui.screen

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    val context = LocalContext.current
    val inputViewRef = remember { mutableStateOf<TerminalInputView?>(null) }

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

    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    Box(modifier = modifier.fillMaxSize()) {
        AndroidExternalSurface(
            modifier = Modifier
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

                // Ensure the IME proxy view has focus so software keyboards work
                val inputView = inputViewRef.value
                if (inputView != null && inputView.windowToken != null) {
                    inputView.requestFocus()
                    imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
                }

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

        AndroidView(
            factory = { ctx ->
                TerminalInputView(ctx).apply {
                    terminalSession = session
                    inputViewRef.value = this
                }
            },
            modifier = Modifier.size(1.dp)
        )
    }
}
