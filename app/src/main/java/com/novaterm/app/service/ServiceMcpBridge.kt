package com.novaterm.app.service

import android.util.Log
import com.novaterm.core.mcp.bridge.FileEntry
import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.bridge.McpSessionInfo
import com.novaterm.core.mcp.bridge.McpWorkspaceInfo
import com.novaterm.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Bridge between the MCP server and TerminalService internals.
 *
 * Extracted from TerminalService to decouple MCP protocol handling
 * from service lifecycle. Communicates with the service through
 * functional interfaces to avoid circular dependencies.
 */
class ServiceMcpBridge(
    private val sessionsFlow: StateFlow<List<TerminalSession>>,
    private val serviceScope: CoroutineScope,
    private val onCreateSession: (cwd: String?) -> TerminalSession?,
    private val onRemoveSession: (index: Int) -> Unit,
    private val defaultCwd: () -> String,
    private val prefixPath: () -> String,
    private val onGetAgentOrchestrator: () -> com.novaterm.core.session.manager.AgentOrchestrator? = { null },
) : McpSessionBridge {

    private val _mcpSessions = MutableStateFlow<List<McpSessionInfo>>(emptyList())

    override val sessions: StateFlow<List<McpSessionInfo>> get() = _mcpSessions

    init {
        // Keep MCP session list continuously in sync with terminal sessions.
        // Observing the flow catches unexpected session terminations that
        // don't go through closeSession() (e.g., process crash).
        serviceScope.launch {
            sessionsFlow.collect { syncMcpSessions() }
        }
    }

    private fun syncMcpSessions() {
        _mcpSessions.value = sessionsFlow.value.mapIndexed { index, session ->
            McpSessionInfo(
                index = index,
                title = session.title?.takeIf { it.isNotBlank() } ?: "shell",
                cwd = session.cwd ?: defaultCwd(),
                isRunning = session.isRunning,
                pid = session.pid,
            )
        }
    }

    override fun createSession(cwd: String?): Int {
        val session = onCreateSession(cwd)
        syncMcpSessions()
        return if (session != null) sessionsFlow.value.indexOf(session) else -1
    }

    override fun closeSession(index: Int) {
        onRemoveSession(index)
        syncMcpSessions()
    }

    override fun writeToSession(index: Int, input: String) {
        val sessionList = sessionsFlow.value
        if (index !in sessionList.indices) return
        sessionList[index].write(input)
    }

    override fun readOutput(index: Int, lines: Int): String {
        val sessionList = sessionsFlow.value
        if (index !in sessionList.indices) return ""
        val emulator = sessionList[index].emulator ?: return ""
        val screen = emulator.screen
        val transcript = screen.getTranscriptText()
        val allLines = transcript.split("\n")
        return allLines.takeLast(lines.coerceAtLeast(1)).joinToString("\n")
    }

    override fun getSessionCwd(index: Int): String? {
        val sessionList = sessionsFlow.value
        if (index !in sessionList.indices) return null
        return sessionList[index].cwd
    }

    override fun homePath(): String = defaultCwd()

    override fun prefixPath(): String = prefixPath.invoke()

    override fun readFile(path: String, maxBytes: Int): String? {
        return try {
            val file = java.io.File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) return null
            if (file.length() > maxBytes) return null
            file.readText()
        } catch (e: Exception) {
            Log.e(TAG, "MCP readFile error: $path", e)
            null
        }
    }

    override fun writeFile(path: String, content: String): Boolean {
        return try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "MCP writeFile error: $path", e)
            false
        }
    }

    override fun listDirectory(path: String): List<FileEntry> {
        return try {
            val dir = java.io.File(path)
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            dir.listFiles()?.map { file ->
                FileEntry(
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                )
            }?.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name })
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "MCP listDirectory error: $path", e)
            emptyList()
        }
    }

    // ── Agent workspace access ──────────────────────────────────

    override fun listWorkspaces(): List<McpWorkspaceInfo> {
        val orchestrator = onGetAgentOrchestrator() ?: return emptyList()
        return orchestrator.allWorkspaces.map { workspace ->
            McpWorkspaceInfo(
                id = workspace.id,
                name = workspace.displayName,
                agentType = workspace.agentType.label,
                status = workspace.status.name.lowercase(),
                sessionId = workspace.sessionId,
                pid = workspace.pid,
                workingDir = workspace.workingDir,
                runtimeSeconds = workspace.runtimeSeconds,
                lastOutputAt = workspace.lastOutputAt,
            )
        }
    }

    override fun getWorkspace(workspaceId: String): McpWorkspaceInfo? {
        val orchestrator = onGetAgentOrchestrator() ?: return null
        val workspace = orchestrator.findById(workspaceId) ?: return null
        return McpWorkspaceInfo(
            id = workspace.id,
            name = workspace.displayName,
            agentType = workspace.agentType.label,
            status = workspace.status.name.lowercase(),
            sessionId = workspace.sessionId,
            pid = workspace.pid,
            workingDir = workspace.workingDir,
            runtimeSeconds = workspace.runtimeSeconds,
            lastOutputAt = workspace.lastOutputAt,
        )
    }

    override fun getAgentOutput(workspaceId: String, lines: Int): String {
        val orchestrator = onGetAgentOrchestrator() ?: return ""
        val workspace = orchestrator.findById(workspaceId) ?: return ""
        if (workspace.sessionId < 0) return ""
        return readOutput(workspace.sessionId, lines)
    }

    override fun runAgentDiff(workspaceId: String): String {
        val orchestrator = onGetAgentOrchestrator() ?: return ""
        return orchestrator.runDiff(workspaceId)
    }

    override fun approveAgentChanges(workspaceId: String, message: String): Boolean {
        val orchestrator = onGetAgentOrchestrator() ?: return false
        return orchestrator.approveChanges(workspaceId, message)
    }

    override fun rejectAgentChanges(workspaceId: String): Boolean {
        val orchestrator = onGetAgentOrchestrator() ?: return false
        return orchestrator.rejectChanges(workspaceId)
    }

    companion object {
        private const val TAG = "NovaTerm"
    }
}
