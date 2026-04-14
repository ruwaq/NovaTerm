package com.novaterm.core.mcp.bridge

import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge between MCP tools and the terminal service.
 *
 * Implemented by TerminalService in the app module. The MCP module
 * calls these methods; the app module provides the implementation.
 * This avoids a circular dependency (core/mcp cannot depend on app).
 *
 * All methods are thread-safe. Implementations must handle
 * main-thread dispatch internally when needed.
 */
interface McpSessionBridge {

    /** Observable list of active sessions. */
    val sessions: StateFlow<List<McpSessionInfo>>

    /** Create a new terminal session. Returns session index or -1 on failure. */
    fun createSession(cwd: String? = null): Int

    /** Close a terminal session by index. */
    fun closeSession(index: Int)

    /** Write text to a session's PTY input (as if typed by user). */
    fun writeToSession(index: Int, input: String)

    /** Read recent output from a session's scrollback buffer. */
    fun readOutput(index: Int, lines: Int = 100): String

    /** Get the current working directory of a session. */
    fun getSessionCwd(index: Int): String?

    /** Get the home directory path. */
    fun homePath(): String

    /** Get the prefix directory path ($PREFIX). */
    fun prefixPath(): String

    /** Read a file from the filesystem. Null if not found or not readable. */
    fun readFile(path: String, maxBytes: Int = 1_000_000): String?

    /** Write content to a file. Returns true on success. */
    fun writeFile(path: String, content: String): Boolean

    /** List directory contents. Returns file names or empty on error. */
    fun listDirectory(path: String): List<FileEntry>

    // ── Agent workspace access ──────────────────────────────────

    /** List all agent workspaces with their status. */
    fun listWorkspaces(): List<McpWorkspaceInfo>

    /** Get a single workspace by ID. Null if not found. */
    fun getWorkspace(workspaceId: String): McpWorkspaceInfo?

    /** Read recent output from an agent workspace's session. */
    fun getAgentOutput(workspaceId: String, lines: Int = 50): String

    /** Run git diff in a workspace's directory. Returns raw unified diff. */
    fun runAgentDiff(workspaceId: String): String

    /** Approve (commit) all changes in a workspace. Returns true on success. */
    fun approveAgentChanges(workspaceId: String, message: String = ""): Boolean

    /** Reject (discard) all changes in a workspace. Returns true on success. */
    fun rejectAgentChanges(workspaceId: String): Boolean
}

/** Lightweight session info exposed to MCP tools. */
data class McpSessionInfo(
    val index: Int,
    val title: String,
    val cwd: String,
    val isRunning: Boolean,
    val pid: Int,
)

/** Agent workspace info exposed to MCP tools. */
data class McpWorkspaceInfo(
    val id: String,
    val name: String,
    val agentType: String,
    val status: String,
    val sessionId: Int,
    val pid: Int,
    val workingDir: String,
    val runtimeSeconds: Long,
    val lastOutputAt: Long,
)

/** Directory entry for file listing. */
data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)
