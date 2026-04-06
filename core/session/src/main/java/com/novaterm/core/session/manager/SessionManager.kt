package com.novaterm.core.session.manager

import com.novaterm.core.common.contract.SessionEvent
import com.novaterm.core.common.contract.SessionManager
import com.novaterm.core.common.contract.ShellProvider
import com.novaterm.core.common.contract.TerminalEngine
import com.novaterm.core.common.contract.TerminalEngineFactory
import com.novaterm.core.common.model.SessionInfo
import com.novaterm.core.common.model.SessionStatus
import com.novaterm.core.common.model.TerminalDimensions
import com.novaterm.core.common.util.OpResult
import com.novaterm.core.common.util.runCatchingOp
import com.novaterm.terminal.TerminalEmulator
import com.novaterm.terminal.TerminalSession
import com.novaterm.terminal.TerminalSessionClient
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicInteger

class SessionManager(
    private val shellProvider: ShellProvider,
    private val transcriptRows: Int = TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
    private val engineFactory: TerminalEngineFactory? = null,
) : SessionManager {

    companion object {
        private const val TAG = "SessionManager"
    }

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

        val ptySession = TerminalSession(
            resolvedShell,
            resolvedCwd,
            arrayOf(resolvedShell),
            envArray,
            transcriptRows,
            requireClient(),
        )

        // Create Rust engine if factory is provided and wire byte interceptor
        val engine = engineFactory?.let { factory ->
            try {
                val dims = TerminalDimensions(rows = 24, columns = 80)
                val eng = factory.create(dims)
                ptySession.setRawByteInterceptor { data, length ->
                    try {
                        eng.processBytes(data.copyOf(length))
                    } catch (e: Exception) {
                        Log.e(TAG, "Rust engine processBytes error", e)
                    }
                }
                if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "Rust engine attached to session $id")
                eng
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Rust engine, falling back to Java-only", e)
                null
            }
        }

        val managed = ManagedSession(
            id = id,
            ptySession = ptySession,
            initialCwd = resolvedCwd,
            engine = engine,
        )

        val newIndex: Int
        synchronized(lock) {
            sessionMap[id] = managed
            newIndex = sessionMap.size - 1
            publishState()
        }
        _activeSessionIndex.value = newIndex
        // Channel.UNLIMITED never returns failure, safe to ignore result
        _events.trySend(SessionEvent.Created(id))
        id
    }

    override fun destroySession(sessionId: Int) {
        val managed: ManagedSession?
        val remainingSize: Int
        synchronized(lock) {
            managed = sessionMap.remove(sessionId)
            remainingSize = sessionMap.size
            if (managed != null) publishState()
        }
        managed ?: return

        // Clean up Rust engine resources outside lock (may be slow)
        managed.engine?.destroy()
        managed.ptySession.setRawByteInterceptor(null)

        val exitCode = if (managed.ptySession.isRunning) {
            managed.ptySession.finishIfRunning()
            null
        } else {
            managed.ptySession.exitStatus
        }
        // Channel.UNLIMITED never returns failure, safe to ignore result
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
        getManaged(sessionId)?.ptySession?.write(data, 0, data.size)
    }

    override fun resizeSession(sessionId: Int, dimensions: TerminalDimensions) {
        val managed = getManaged(sessionId) ?: return
        managed.ptySession.updateSize(dimensions.columns, dimensions.rows, 0, 0)
        managed.engine?.resize(dimensions)
    }

    override fun sendSignal(sessionId: Int, signal: Int) {
        getManaged(sessionId)?.ptySession?.let { session ->
            android.os.Process.sendSignal(session.pid, signal)
        }
    }

    fun getSession(sessionId: Int): TerminalSession? =
        getManaged(sessionId)?.ptySession

    fun getSessionByIndex(index: Int): TerminalSession? {
        val entries = synchronized(lock) { sessionMap.entries.toList() }
        return entries.getOrNull(index)?.value?.ptySession
    }

    fun sessionCount(): Int = synchronized(lock) { sessionMap.size }

    /** Get the Rust terminal engine for a session, if one is attached. */
    fun getEngine(sessionId: Int): TerminalEngine? =
        getManaged(sessionId)?.engine

    fun destroyAll() {
        val sessions: List<ManagedSession>
        synchronized(lock) {
            sessions = sessionMap.values.toList()
            sessionMap.clear()
            publishState()
        }
        sessions.forEach { managed ->
            managed.engine?.destroy()
            managed.ptySession.setRawByteInterceptor(null)
            val exitCode = if (managed.ptySession.isRunning) {
                managed.ptySession.finishIfRunning()
                null
            } else {
                managed.ptySession.exitStatus
            }
            _events.trySend(SessionEvent.Destroyed(managed.id, exitCode))
        }
    }

    /** Notify that a session title changed (called from TerminalSessionClient). */
    fun notifyTitleChanged(sessionId: Int, title: String) {
        _events.trySend(SessionEvent.TitleChanged(sessionId, title))
        synchronized(lock) { publishState() }
    }

    private fun getManaged(sessionId: Int): ManagedSession? =
        synchronized(lock) { sessionMap[sessionId] }

    /** Must be called while holding [lock]. */
    private fun publishState() {
        val entries = sessionMap.entries.toList()
        _sessions.value = entries.map { (_, managed) ->
            val ts = managed.ptySession
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
    val ptySession: TerminalSession,
    val initialCwd: String,
    val engine: TerminalEngine? = null,
)
