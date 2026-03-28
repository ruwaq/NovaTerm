package com.novaterm.core.session.manager

import com.novaterm.core.common.contract.SessionEvent
import com.novaterm.core.common.contract.SessionManager
import com.novaterm.core.common.contract.ShellProvider
import com.novaterm.core.common.model.SessionInfo
import com.novaterm.core.common.model.SessionStatus
import com.novaterm.core.common.model.TerminalDimensions
import com.novaterm.core.common.util.OpResult
import com.novaterm.core.common.util.runCatchingOp
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicInteger

class TermuxSessionManager(
    private val shellProvider: ShellProvider,
    private val transcriptRows: Int = TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
) : SessionManager {

    private val nextId = AtomicInteger(1)

    /**
     * All access to [sessionMap] MUST be inside synchronized(lock).
     * Using a dedicated lock object avoids exposing the monitor.
     */
    private val lock = Any()
    private val sessionMap = LinkedHashMap<Int, ManagedSession>()

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    override val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private val _activeSessionIndex = MutableStateFlow(0)
    override val activeSessionIndex: StateFlow<Int> = _activeSessionIndex.asStateFlow()

    private val _events = Channel<SessionEvent>(Channel.UNLIMITED)
    override val events: Flow<SessionEvent> = _events.receiveAsFlow()

    private var client: TerminalSessionClient? = null

    fun setClient(client: TerminalSessionClient) {
        this.client = client
    }

    private fun requireClient(): TerminalSessionClient =
        client ?: throw IllegalStateException(
            "TerminalSessionClient not set. Call setClient() before creating sessions."
        )

    override fun createSession(
        shell: String?,
        cwd: String?,
        env: Map<String, String>,
    ): OpResult<Int> = runCatchingOp {
        val id = nextId.getAndIncrement()
        val resolvedShell = shell ?: shellProvider.findShell()
        val resolvedCwd = cwd ?: shellProvider.defaultWorkingDirectory()
        val envArray = shellProvider.buildEnvironment(env)

        val termuxSession = TerminalSession(
            resolvedShell,
            resolvedCwd,
            arrayOf(resolvedShell),
            envArray,
            transcriptRows,
            requireClient(),
        )

        val managed = ManagedSession(
            id = id,
            termuxSession = termuxSession,
            initialCwd = resolvedCwd,
        )

        val newIndex: Int
        synchronized(lock) {
            sessionMap[id] = managed
            newIndex = sessionMap.size - 1
        }

        publishState()
        _activeSessionIndex.value = newIndex
        _events.trySend(SessionEvent.Created(id))
        id
    }

    override fun destroySession(sessionId: Int) {
        val managed: ManagedSession?
        val remainingSize: Int
        synchronized(lock) {
            managed = sessionMap.remove(sessionId)
            remainingSize = sessionMap.size
        }
        managed ?: return

        val exitCode = if (managed.termuxSession.isRunning) {
            managed.termuxSession.finishIfRunning()
            null
        } else {
            managed.termuxSession.exitStatus
        }

        publishState()
        _events.trySend(SessionEvent.Destroyed(sessionId, exitCode))

        val currentIndex = _activeSessionIndex.value
        if (currentIndex >= remainingSize && remainingSize > 0) {
            _activeSessionIndex.value = remainingSize - 1
        } else if (remainingSize == 0) {
            _activeSessionIndex.value = 0
        }
    }

    override fun selectSession(sessionId: Int) {
        val index = synchronized(lock) {
            sessionMap.keys.indexOf(sessionId)
        }
        if (index >= 0) {
            _activeSessionIndex.value = index
        }
    }

    fun selectSessionByIndex(index: Int) {
        val valid = synchronized(lock) { index in 0 until sessionMap.size }
        if (valid) {
            _activeSessionIndex.value = index
        }
    }

    override fun writeToSession(sessionId: Int, data: ByteArray) {
        getManaged(sessionId)?.termuxSession?.write(data, 0, data.size)
    }

    override fun resizeSession(sessionId: Int, dimensions: TerminalDimensions) {
        getManaged(sessionId)?.termuxSession?.updateSize(
            dimensions.columns, dimensions.rows, 0, 0,
        )
    }

    override fun sendSignal(sessionId: Int, signal: Int) {
        getManaged(sessionId)?.termuxSession?.let { session ->
            android.os.Process.sendSignal(session.pid, signal)
        }
    }

    fun getTermuxSession(sessionId: Int): TerminalSession? =
        getManaged(sessionId)?.termuxSession

    fun getTermuxSessionByIndex(index: Int): TerminalSession? {
        val entries = synchronized(lock) { sessionMap.entries.toList() }
        return entries.getOrNull(index)?.value?.termuxSession
    }

    fun sessionCount(): Int = synchronized(lock) { sessionMap.size }

    fun destroyAll() {
        val sessions: List<ManagedSession>
        synchronized(lock) {
            sessions = sessionMap.values.toList()
            sessionMap.clear()
        }
        sessions.forEach { managed ->
            val exitCode = if (managed.termuxSession.isRunning) {
                managed.termuxSession.finishIfRunning()
                null
            } else {
                managed.termuxSession.exitStatus
            }
            _events.trySend(SessionEvent.Destroyed(managed.id, exitCode))
        }
        publishState()
    }

    /** Notify that a session title changed (called from TerminalSessionClient). */
    fun notifyTitleChanged(sessionId: Int, title: String) {
        _events.trySend(SessionEvent.TitleChanged(sessionId, title))
        publishState()
    }

    private fun getManaged(sessionId: Int): ManagedSession? =
        synchronized(lock) { sessionMap[sessionId] }

    private fun publishState() {
        val entries = synchronized(lock) { sessionMap.entries.toList() }
        _sessions.value = entries.map { (_, managed) ->
            val ts = managed.termuxSession
            val isRunning = ts.isRunning
            SessionInfo(
                id = managed.id,
                pid = ts.pid,
                title = ts.title?.takeIf { it.isNotBlank() } ?: "shell",
                cwd = managed.initialCwd,
                status = if (isRunning) SessionStatus.RUNNING else SessionStatus.FINISHED,
                exitCode = if (isRunning) null else ts.exitStatus,
            )
        }
    }
}

private data class ManagedSession(
    val id: Int,
    val termuxSession: TerminalSession,
    val initialCwd: String,
)
