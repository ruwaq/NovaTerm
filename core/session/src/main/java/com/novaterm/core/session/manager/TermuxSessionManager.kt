package com.novaterm.core.session.manager

import com.novaterm.core.common.contract.SessionManager
import com.novaterm.core.common.contract.ShellProvider
import com.novaterm.core.common.model.SessionInfo
import com.novaterm.core.common.model.SessionStatus
import com.novaterm.core.common.model.TerminalDimensions
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

class TermuxSessionManager(
    private val shellProvider: ShellProvider,
    private val transcriptRows: Int = TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
) : SessionManager {

    private val nextId = AtomicInteger(1)
    private val sessionMap = LinkedHashMap<Int, ManagedSession>()

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    override val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private val _activeSessionIndex = MutableStateFlow(0)
    override val activeSessionIndex: StateFlow<Int> = _activeSessionIndex.asStateFlow()

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
    ): Int {
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

        val newSize: Int
        synchronized(sessionMap) {
            sessionMap[id] = managed
            newSize = sessionMap.size
        }

        publishState()
        _activeSessionIndex.value = newSize - 1
        return id
    }

    override fun destroySession(sessionId: Int) {
        val managed: ManagedSession?
        val remainingSize: Int
        synchronized(sessionMap) {
            managed = sessionMap.remove(sessionId)
            remainingSize = sessionMap.size
        }
        managed ?: return
        managed.termuxSession.finishIfRunning()
        publishState()

        val currentIndex = _activeSessionIndex.value
        if (currentIndex >= remainingSize && remainingSize > 0) {
            _activeSessionIndex.value = remainingSize - 1
        }
    }

    override fun selectSession(sessionId: Int) {
        val index = synchronized(sessionMap) {
            sessionMap.keys.indexOf(sessionId)
        }
        if (index >= 0) {
            _activeSessionIndex.value = index
        }
    }

    fun selectSessionByIndex(index: Int) {
        if (index in 0 until sessionMap.size) {
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
        val entries = synchronized(sessionMap) { sessionMap.entries.toList() }
        return entries.getOrNull(index)?.value?.termuxSession
    }

    fun sessionCount(): Int = sessionMap.size

    fun destroyAll() {
        synchronized(sessionMap) {
            sessionMap.values.forEach { it.termuxSession.finishIfRunning() }
            sessionMap.clear()
        }
        publishState()
    }

    private fun getManaged(sessionId: Int): ManagedSession? =
        synchronized(sessionMap) { sessionMap[sessionId] }

    private fun publishState() {
        val entries = synchronized(sessionMap) { sessionMap.entries.toList() }
        _sessions.value = entries.map { (_, managed) ->
            val ts = managed.termuxSession
            SessionInfo(
                id = managed.id,
                pid = ts.pid,
                title = ts.title ?: "shell",
                cwd = managed.initialCwd,
                status = if (ts.isRunning) SessionStatus.RUNNING else SessionStatus.FINISHED,
                exitCode = if (ts.isRunning) null else ts.exitStatus,
            )
        }
    }
}

private data class ManagedSession(
    val id: Int,
    val termuxSession: TerminalSession,
    val initialCwd: String,
)
