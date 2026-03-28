package com.novaterm.core.common.contract

import com.novaterm.core.common.model.SessionInfo
import com.novaterm.core.common.model.TerminalDimensions
import com.novaterm.core.common.util.OpResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle events emitted by the session manager.
 * Consumers (ViewModel, Service) can collect this to react to session changes.
 */
sealed interface SessionEvent {
    data class Created(val sessionId: Int) : SessionEvent
    data class Destroyed(val sessionId: Int, val exitCode: Int?) : SessionEvent
    data class TitleChanged(val sessionId: Int, val title: String) : SessionEvent
}

/**
 * Contract for managing terminal sessions.
 * Implementations handle the actual PTY creation and lifecycle.
 */
interface SessionManager {

    val sessions: StateFlow<List<SessionInfo>>

    val activeSessionIndex: StateFlow<Int>

    /** One-shot events for session lifecycle changes. */
    val events: Flow<SessionEvent>

    /**
     * Create a new terminal session.
     * Returns the session ID on success, or a failure with the reason.
     */
    fun createSession(
        shell: String? = null,
        cwd: String? = null,
        env: Map<String, String> = emptyMap(),
    ): OpResult<Int>

    fun destroySession(sessionId: Int)

    fun selectSession(sessionId: Int)

    fun writeToSession(sessionId: Int, data: ByteArray)

    fun writeToSession(sessionId: Int, text: String) =
        writeToSession(sessionId, text.toByteArray())

    fun resizeSession(sessionId: Int, dimensions: TerminalDimensions)

    fun sendSignal(sessionId: Int, signal: Int)
}
