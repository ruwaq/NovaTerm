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
}
