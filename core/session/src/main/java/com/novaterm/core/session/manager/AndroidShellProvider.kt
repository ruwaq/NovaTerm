package com.novaterm.core.session.manager

import android.content.Context
import com.novaterm.core.common.contract.ShellProvider
import java.io.File

/**
 * Locates shell binaries and constructs environment for terminal sessions.
 * Searches the app's private prefix first, then falls back to system shell.
 *
 * Delegates to:
 * - [EnvironmentBuilder] for env var construction + API key export
 * - [HomeDirectorySetup] for first-run directory structure + storage symlinks
 * - [ShellConfigWriter] for shell config files (.profile, .bashrc, etc.)
 *
 * Also supports per-agent environments via [buildAgentEnvironment] and
 * [buildAgentCommand] for the Agent Orchestrator.
 */
class AndroidShellProvider(
    private val context: Context,
    private val appVersion: String = "0.1.0",
) : ShellProvider {

    // rootDir = context.filesDir = /data/data/com.nvterm/files
    private val rootDir: String get() = context.filesDir.absolutePath
    private val prefix: String get() = "$rootDir/usr"
    private val homeDir: String get() = "$rootDir/home"

    // The system linker executes binaries from /data/ on Android 10+
    // where W^X (Write XOR Execute) blocks direct execution.
    private val systemLinker: String = if (File("/system/bin/linker64").exists()) {
        "/system/bin/linker64"
    } else {
        "/system/bin/linker"
    }

    private val shellConfigWriter = ShellConfigWriter(context, appVersion)
    private val homeSetup = HomeDirectorySetup(shellConfigWriter)
    private val envBuilder = EnvironmentBuilder(
        context = context,
        appVersion = appVersion,
        prefix = prefix,
        homeDir = homeDir,
        rootDir = rootDir,
        shellFinder = { findShell() },
    )

    override fun findShell(): String {
        val candidates = listOf(
            "$prefix/bin/login",
            "$prefix/bin/bash",
            "$prefix/bin/sh",
        )
        return candidates.firstOrNull { File(it).exists() } ?: "/system/bin/sh"
    }

    /**
     * Returns the executable and arguments to launch the shell.
     *
     * Execution chain for W^X bypass:
     *   linker64 → dash (our sh) → login script → sets LD_PRELOAD → exec bash
     *
     * The login script is critical because it configures termux-exec
     * which intercepts ALL child exec() calls and routes them through
     * linker64. Without this, only the shell itself works but ls, apt,
     * git etc. all fail with "Permission denied".
     */
    fun shellCommand(): Array<String> {
        val shell = findShell()
        if (shell.startsWith("/system/")) {
            return arrayOf(shell)
        }

        val loginScript = "$prefix/bin/login"
        val sh = "$prefix/bin/sh"

        return if (File(loginScript).exists() && File(sh).exists()) {
            arrayOf(systemLinker, sh, loginScript)
        } else if (File(shell).exists()) {
            arrayOf(systemLinker, shell, "--login")
        } else {
            arrayOf("/system/bin/sh")
        }
    }

    override fun buildEnvironment(extraVars: Map<String, String>): Array<String> =
        envBuilder.buildEnvironment(extraVars)

    override fun defaultWorkingDirectory(): String =
        homeSetup.ensureHomeDirectory(homeDir, prefix, rootDir)

    /**
     * Build environment for an agent workspace.
     *
     * Merges the base shell environment with workspace-specific overrides
     * (AGENT_NAME, WORKSPACE_ID, GIT_DIR, etc.) while preserving
     * all critical Android/W^X vars (LD_PRELOAD, PREFIX, PATH).
     */
    fun buildAgentEnvironment(workspace: AgentWorkspace): Array<String> {
        val rows = workspace.envOverrides["LINES"] ?: "40"
        val cols = workspace.envOverrides["COLUMNS"] ?: "120"
        val merged = workspace.envOverrides + mapOf("LINES" to rows, "COLUMNS" to cols)
        return envBuilder.buildEnvironment(merged)
    }

    /**
     * Build the command array to launch an agent inside a shell session.
     *
     * On Android we can't exec the agent binary directly — we launch
     * the shell first (for W^X bypass), then write the agent command
     * via PTY. This method returns the shell command; the agent command
     * is written separately via [AgentOrchestrator.buildLaunchCommand].
     */
    fun agentShellCommand(): Array<String> = shellCommand()

    /**
     * The home directory path for agent workspace creation.
     */
    val agentHomeDir: String get() = homeDir
}
