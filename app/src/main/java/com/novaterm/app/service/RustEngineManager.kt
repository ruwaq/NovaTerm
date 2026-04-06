package com.novaterm.app.service

import android.util.Log
import com.novaterm.app.BuildConfig
import com.novaterm.core.common.contract.TerminalEngine
import com.novaterm.core.common.model.TerminalDimensions
import com.novaterm.core.session.engine.RustEngine
import com.novaterm.terminal.TerminalSession

/**
 * Manages Rust VT engine lifecycle for dual-run mode (Phase 2).
 *
 * Handles creation, attachment, detachment, and cleanup of Rust
 * engines that run in parallel with the Java terminal emulator.
 */
class RustEngineManager {

    private val engines = java.util.concurrent.ConcurrentHashMap<String, TerminalEngine>()
    @Volatile private var factory: RustEngine.Factory? = null

    /** Get the Rust engine for a session handle, if one exists. */
    fun getEngine(sessionHandle: String): TerminalEngine? = engines[sessionHandle]

    /**
     * Attach a Rust VT engine to a session for dual-run validation.
     * Sets up the raw byte interceptor and resize listener on the session.
     */
    fun attachEngine(session: TerminalSession) {
        try {
            val f = factory ?: RustEngine.Factory().also { factory = it }
            val engine = f.create(TerminalDimensions(rows = 24, columns = 80))

            session.setRawByteInterceptor { data, length ->
                try {
                    engine.processBytes(data.copyOf(length))
                    // Write back terminal responses (DA, DSR) to PTY
                    val ptyResponse = engine.drainPtyWrites()
                    if (ptyResponse != null) {
                        session.write(ptyResponse, 0, ptyResponse.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Rust engine processBytes error", e)
                }
            }

            session.setResizeListener { rows, cols ->
                try {
                    engine.resize(TerminalDimensions(rows = rows, columns = cols))
                    if (BuildConfig.DEBUG) Log.d(TAG, "Rust engine resized: ${rows}x${cols}")
                } catch (e: Exception) {
                    Log.e(TAG, "Rust engine resize error", e)
                }
            }

            engines[session.mHandle] = engine
            Log.i(TAG, "Rust engine attached to session ${session.mHandle}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Rust engine, running Java-only", e)
        }
    }

    /** Detach and destroy the Rust engine for a session. */
    fun detachEngine(session: TerminalSession) {
        engines.remove(session.mHandle)?.destroy()
        session.setRawByteInterceptor(null)
        session.setResizeListener(null)
    }

    /** Destroy all engines and clean up session interceptors. */
    fun destroyAll(sessions: List<TerminalSession>) {
        sessions.forEach { session ->
            engines.remove(session.mHandle)?.destroy()
            session.setRawByteInterceptor(null)
            session.setResizeListener(null)
        }
        engines.clear()
    }

    companion object {
        private const val TAG = "NovaTerm"
    }
}
