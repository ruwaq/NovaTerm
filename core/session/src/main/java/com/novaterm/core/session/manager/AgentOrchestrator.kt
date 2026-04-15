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

    /**
     * Run `git diff` in the workspace's directory and return the raw output.
     *
     * Executes git as a separate Process (doesn't interfere with the agent's PTY).
     * Returns empty string if the workspace doesn't exist or git isn't available.
     */
    fun runDiff(workspaceId: String): String {
        val workspace = workspaces[workspaceId] ?: return ""
        return runGitCommand(workspace.workingDir, "diff")
    }

    /**
     * Run `git diff --stat` for a quick summary of changed files.
     */
    fun runDiffStat(workspaceId: String): String {
        val workspace = workspaces[workspaceId] ?: return ""
        return runGitCommand(workspace.workingDir, "diff", "--stat")
    }

    /**
     * Approve changes in a workspace: `git add -A && git commit`.
     *
     * @param workspaceId The workspace ID
     * @param message Commit message (defaults to "agent: <displayName>")
     * @return true if the commit succeeded
     */
    fun approveChanges(workspaceId: String, message: String = ""): Boolean {
        val workspace = workspaces[workspaceId] ?: return false
        val commitMsg = message.ifBlank { "agent: ${workspace.displayName}" }
        val addResult = runGitCommand(workspace.workingDir, "add", "-A")
        Log.i(TAG, "git add -A: $addResult")
        val commitResult = runGitCommand(workspace.workingDir, "commit", "-m", commitMsg)
        Log.i(TAG, "git commit: $commitResult")
        return commitResult.contains("] ") // e.g. "[main abc1234]"
    }

    /**
     * Reject changes in a workspace: `git checkout . && git clean -fd`.
     *
     * Lists untracked files first for logging, then removes them.
     * Only runs `clean` if `checkout` succeeded (no point cleaning if checkout failed).
     *
     * @return true if the reset succeeded
     */
    fun rejectChanges(workspaceId: String): Boolean {
        val workspace = workspaces[workspaceId] ?: return false
        val checkoutResult = runGitCommand(workspace.workingDir, "checkout", ".")
        Log.i(TAG, "git checkout .: $checkoutResult")
        // List untracked files before removing (for audit trail)
        val untracked = runGitCommand(workspace.workingDir, "ls-files", "--others", "--exclude-standard")
        if (untracked.isNotEmpty()) {
            Log.i(TAG, "Untracked files to remove: $untracked")
            val cleanResult = runGitCommand(workspace.workingDir, "clean", "-fd")
            Log.i(TAG, "git clean -fd: $cleanResult")
        } else {
            Log.i(TAG, "No untracked files to clean")
        }
        return true
    }

    /**
     * Execute a git command in a directory and capture stdout.
     * Runs as a separate Process — doesn't touch the agent's PTY session.
     *
     * Times out after 30 seconds to prevent indefinite blocking if git hangs
     * (e.g., large repo, lock file, NFS mount).
     */
    private fun runGitCommand(workingDir: String, vararg args: String): String {
        return try {
            val command = arrayOf("git", *args)
            val process = ProcessBuilder(*command)
                .directory(File(workingDir))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.w(TAG, "git ${args.joinToString(" ")} timed out after 30s in $workingDir")
                ""
            } else {
                output.trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "git ${args.joinToString(" ")} failed: ${e.message}")
            ""
        }
    }

    companion object {
        private const val TAG = "AgentOrchestrator"
    }
}