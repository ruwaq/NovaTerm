package com.novaterm.core.session.manager

import java.util.UUID

/**
 * Represents an isolated workspace for an AI agent.
 *
 * Each workspace gets its own PTY session, environment variables,
 * and working directory. This enables parallel agent execution
 * without interference — the core Superset-like pattern adapted for
 * Android (where git worktrees aren't practical).
 *
 * Key design decisions:
 * - Workspaces use directory isolation + GIT_DIR/GIT_WORK_TREE
 *   instead of git worktrees (Android filesystem constraints)
 * - Each workspace has a unique ID for lifecycle tracking
 * - Status is observable for real-time dashboard updates
 */
data class AgentWorkspace(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val agentType: AgentType,
    val sessionId: Int = -1,        // Index into TerminalService sessions, -1 = not launched
    val workingDir: String,          // Isolated working directory for the agent
    val envOverrides: Map<String, String> = emptyMap(),
    val preset: AgentPreset? = null,
    val status: AgentStatus = AgentStatus.IDLE,
    val pid: Int = -1,
    val startedAt: Long = 0,
    val lastOutputAt: Long = 0,
    val exitCode: Int? = null,
) {
    /** Runtime in seconds. */
    val runtimeSeconds: Long
        get() = if (startedAt == 0L) 0L
        else if (status == AgentStatus.RUNNING) (System.currentTimeMillis() - startedAt) / 1000
        else if (exitCode != null) (lastOutputAt - startedAt) / 1000
        else 0L

    /** Human-readable agent type. */
    val displayName: String
        get() = name.ifBlank { agentType.label }

    /** Is this workspace currently active? */
    val isActive: Boolean
        get() = status == AgentStatus.RUNNING || status == AgentStatus.IDLE
}

/**
 * The type of AI agent running in the workspace.
 * Each type has different launch commands, env vars, and behavior.
 */
enum class AgentType(val label: String, val command: String, val defaultArgs: List<String>) {
    CLAUDE_CODE(
        label = "Claude Code",
        command = "claude",
        defaultArgs = listOf("--dangerously-skip-permissions"),
    ),
    GEMINI_CLI(
        label = "Gemini CLI",
        command = "gemini",
        defaultArgs = emptyList(),
    ),
    AIDER(
        label = "Aider",
        command = "aider",
        defaultArgs = listOf("--model", "anthropic/claude-sonnet-4-20250514"),
    ),
    OPENCODE(
        label = "OpenCode",
        command = "opencode",
        defaultArgs = emptyList(),
    ),
    CUSTOM(
        label = "Custom",
        command = "",
        defaultArgs = emptyList(),
    );

    /** Build the full command array for this agent type. */
    fun buildCommand(customArgs: List<String> = emptyList()): Array<String> {
        val args = if (customArgs.isNotEmpty()) customArgs else defaultArgs
        return arrayOf(command, *args.toTypedArray())
    }

    companion object {
        fun fromName(name: String): AgentType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CUSTOM
    }
}

/**
 * Status of an agent workspace.
 * Drives the dashboard UI and determines available actions.
 */
enum class AgentStatus {
    /** Created but not yet launched. */
    IDLE,
    /** Agent process is running. */
    RUNNING,
    /** Agent paused (SIGSTOP). */
    PAUSED,
    /** Agent exited successfully (exit code 0). */
    COMPLETED,
    /** Agent exited with error. */
    ERROR,
    /** Workspace cleaned up. */
    DESTROYED,
}