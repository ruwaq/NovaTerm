package com.novaterm.core.common.contract

import com.novaterm.core.common.model.SessionInfo
import com.novaterm.core.common.model.TerminalDimensions
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for managing terminal sessions.
 * Implementations handle the actual PTY creation and lifecycle.
 */
interface SessionManager {

    val sessions: StateFlow<List<SessionInfo>>

    val activeSessionIndex: StateFlow<Int>

    fun createSession(
        shell: String? = null,
        cwd: String? = null,
        env: Map<String, String> = emptyMap(),
    ): Int

    fun destroySession(sessionId: Int)

    fun selectSession(sessionId: Int)

    fun writeToSession(sessionId: Int, data: ByteArray)

    fun writeToSession(sessionId: Int, text: String) =
        writeToSession(sessionId, text.toByteArray())

    fun resizeSession(sessionId: Int, dimensions: TerminalDimensions)

    fun sendSignal(sessionId: Int, signal: Int)
}
