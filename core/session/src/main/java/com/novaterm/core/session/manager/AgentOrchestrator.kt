package com.novaterm.core.session.manager

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates AI agent workspaces — creating, monitoring, and cleaning up
 * isolated environments for parallel agent execution.
 *
 * This is the core of the Superset-like agent orchestration adapted for Android:
 * - Superset uses git worktrees → we use directory isolation + env vars
 * - Superset runs on desktop → we run on Android (PTY in Termux environment)
 * - Superset monitors via Electron → we monitor via TerminalService + StateFlow
 *
 * Lifecycle:
 * 1. User selects a preset (or custom command)
 * 2. Orchestrator creates an AgentWorkspace with isolated env
 * 3. TerminalService launches a new session with the workspace's env
 * 4. Dashboard observes workspace status in real-time
 * 5. On completion, workspace is marked COMPLETED/ERROR
 * 6. User reviews diff, approves/rejects changes
 */
class AgentOrchestrator(
    private val shellProvider: AndroidShellProvider,
    private val homeDir: String,
) {
    private val workspaces = ConcurrentHashMap<String, AgentWorkspace>()

    /** Reactive state flow for Compose observation. Updated on every mutation. */
    private val _workspacesState = MutableStateFlow<List<AgentWorkspace>>(emptyList())
    val workspacesState: StateFlow<List<AgentWorkspace>> = _workspacesState.asStateFlow()

    /** Emit current workspace list to StateFlow for reactive UI updates. */
    private fun emitWorkspaces() {
        _workspacesState.value = workspaces.values.toList()
    }

    /** All active workspaces, observable for dashboard. */
    val activeWorkspaces: List<AgentWorkspace>
        get() = workspaces.values.filter { it.isActive }

    /** All workspaces (including completed/destroyed), for history. */
    val allWorkspaces: List<AgentWorkspace>
        get() = workspaces.values.toList()

    /**
     * Create a new workspace for an agent.
     *
     * @param preset The agent preset to use
     * @param name Human-readable name (defaults to preset name)
     * @param customWorkingDir Override working directory (empty = auto-create)
     * @param customArgs Override command arguments (empty = use preset defaults)
     * @param extraEnv Additional environment variables
     */
    fun createWorkspace(
        preset: AgentPreset,
        name: String = preset.name,
        customWorkingDir: String = "",
        customArgs: List<String> = emptyList(),
        extraEnv: Map<String, String> = emptyMap(),
    ): AgentWorkspace {
        val workDir = if (customWorkingDir.isNotEmpty()) {
            customWorkingDir
        } else {
            createIsolatedDir(preset.id)
        }

        // Ensure directory exists
        File(workDir).mkdirs()

        val envOverrides = buildMap {
            // Agent identification
            put("AGENT_NAME", name)
            put("AGENT_TYPE", preset.agentType.name)
            put("WORKSPACE_ID", preset.id)

            // Git isolation: use per-workspace GIT_DIR so agents don't
            // interfere with each other's git operations
            put("GIT_DIR", "$workDir/.git")

            // Terminal identification for AI tools
            put("TERM_PROGRAM", "novaterm")
            put("COLORTERM", "truecolor")

            // Agent-specific env
            putAll(preset.envOverrides)
            putAll(extraEnv)
        }

        val workspace = AgentWorkspace(
            name = name,
            agentType = preset.agentType,
            workingDir = workDir,
            envOverrides = envOverrides,
            preset = preset,
            status = AgentStatus.IDLE,
        )

        workspaces[workspace.id] = workspace
        emitWorkspaces()
        Log.i(TAG, "Created workspace: ${workspace.id} (${workspace.displayName}) in $workDir")

        return workspace
    }

    /**
     * Mark a workspace as running after its session is launched.
     *
     * @param workspaceId The workspace ID
     * @param sessionId The TerminalService session index
     * @param pid The process ID of the agent
     */
    fun markRunning(workspaceId: String, sessionId: Int, pid: Int) {
        workspaces[workspaceId]?.let { existing ->
            val updated = existing.copy(
                sessionId = sessionId,
                pid = pid,
                status = AgentStatus.RUNNING,
                startedAt = System.currentTimeMillis(),
                lastOutputAt = System.currentTimeMillis(),
            )
            workspaces[workspaceId] = updated
            emitWorkspaces()
        }
    }

    /**
     * Update the last output timestamp for a workspace.
     * Called by TerminalService when new output arrives.
     */
    fun markOutput(workspaceId: String) {
        workspaces[workspaceId]?.let { existing ->
            workspaces[workspaceId] = existing.copy(lastOutputAt = System.currentTimeMillis())
            emitWorkspaces()
        }
    }

    /**
     * Mark a workspace as completed (agent exited).
     *
     * @param workspaceId The workspace ID
     * @param exitCode The process exit code
     */
    fun markCompleted(workspaceId: String, exitCode: Int) {
        workspaces[workspaceId]?.let { existing ->
            val status = if (exitCode == 0) AgentStatus.COMPLETED else AgentStatus.ERROR
            workspaces[workspaceId] = existing.copy(
                status = status,
                exitCode = exitCode,
                lastOutputAt = System.currentTimeMillis(),
            )
            emitWorkspaces()
        }
    }

    /**
     * Get a workspace by its session index.
     * Used by TerminalService to map sessions back to workspaces.
     */
    fun findBySessionId(sessionId: Int): AgentWorkspace? =
        workspaces.values.firstOrNull { it.sessionId == sessionId }

    /**
     * Get a workspace by its ID.
     */
    fun findById(workspaceId: String): AgentWorkspace? = workspaces[workspaceId]

    /**
     * Adjust all workspace sessionId values after a session is removed.
     *
     * When session at [removedIndex] is removed from the list, all sessions
     * after it shift down by 1. This method updates workspace references
     * so they continue pointing to the correct session.
     *
     * Must be called after every session removal (kill, close, or natural exit).
     */
    fun adjustSessionIdsAfterRemoval(removedIndex: Int) {
        var changed = false
        workspaces.forEach { (id, workspace) ->
            if (workspace.sessionId > removedIndex) {
                workspaces[id] = workspace.copy(sessionId = workspace.sessionId - 1)
                changed = true
            }
        }
        if (changed) emitWorkspaces()
    }

    /**
     * Mark a workspace as paused (SIGSTOP sent to process).
     */
    fun markPaused(workspaceId: String) {
        workspaces[workspaceId]?.let { existing ->
            workspaces[workspaceId] = existing.copy(status = AgentStatus.PAUSED)
            emitWorkspaces()
        }
    }

    /**
     * Mark a workspace as resumed (SIGCONT sent to process).
     */
    fun markResumed(workspaceId: String) {
        workspaces[workspaceId]?.let { existing ->
            workspaces[workspaceId] = existing.copy(
                status = AgentStatus.RUNNING,
                lastOutputAt = System.currentTimeMillis(),
            )
            emitWorkspaces()
        }
    }

    /**
     * Destroy a workspace and clean up its directory.
     */
    fun destroyWorkspace(workspaceId: String) {
        workspaces[workspaceId]?.let { workspace ->
            // Clean up the isolated directory (only if we created it)
            val dir = File(workspace.workingDir)
            if (dir.exists() && dir.absolutePath.startsWith(homeDir)) {
                dir.deleteRecursively()
                Log.i(TAG, "Cleaned up workspace directory: ${workspace.workingDir}")
            }
            workspaces[workspaceId] = workspace.copy(status = AgentStatus.DESTROYED)
            workspaces.remove(workspaceId)
            emitWorkspaces()
        }
    }

    /**
     * Build the environment array for a workspace.
     * Merges the base shell environment with workspace-specific overrides.
     */
    fun buildWorkspaceEnv(workspace: AgentWorkspace): Array<String> {
        return shellProvider.buildEnvironment(workspace.envOverrides)
    }

    /**
     * Build the shell command for launching an agent.
     */
    fun buildLaunchCommand(
        workspace: AgentWorkspace,
        customArgs: List<String> = emptyList(),
    ): Array<String> {
        val args = if (customArgs.isNotEmpty()) customArgs
        else workspace.preset?.args ?: emptyList()
        return arrayOf(workspace.preset?.command ?: workspace.agentType.command, *args.toTypedArray())
    }

    /**
     * Create an isolated working directory for a workspace.
     * Pattern: $homeDir/agent-workspaces/<preset-id>-<timestamp>
     */
    private fun createIsolatedDir(presetId: String): String {
        val timestamp = System.currentTimeMillis()
        val dir = File(homeDir, "agent-workspaces/$presetId-$timestamp")
        dir.mkdirs()
        return dir.absolutePath
    }

    companion object {
        private const val TAG = "AgentOrchestrator"
    }
}