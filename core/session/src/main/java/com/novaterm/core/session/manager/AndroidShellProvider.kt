package com.novaterm.core.session.manager

import android.content.Context
import com.novaterm.core.common.contract.ShellProvider
import java.io.File

/**
 * Locates shell binaries and constructs environment for terminal sessions.
 * Searches the app's private prefix first, then falls back to system shell.
 */
class AndroidShellProvider(
    private val context: Context,
) : ShellProvider {

    private val rootDir: String
        get() = context.filesDir.parentFile?.absolutePath
            ?: context.filesDir.absolutePath

    private val prefix: String get() = "$rootDir/usr"
    private val homeDir: String get() = "$rootDir/home"

    override fun findShell(): String {
        val candidates = listOf(
            "$prefix/bin/zsh",
            "$prefix/bin/bash",
            "$prefix/bin/sh",
            "/system/bin/sh",
        )
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException(
                "No executable shell found. Searched: $candidates"
            )
    }

    override fun buildEnvironment(extraVars: Map<String, String>): Array<String> {
        val env = LinkedHashMap<String, String>()

        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["HOME"] = homeDir
        env["PREFIX"] = prefix
        env["LANG"] = "en_US.UTF-8"
        env["PATH"] = "$prefix/bin:/system/bin"
        env["TMPDIR"] = "$prefix/tmp"
        env["SHELL"] = findShell()

        System.getenv("ANDROID_DATA")?.let { env["ANDROID_DATA"] = it }
        System.getenv("ANDROID_ROOT")?.let { env["ANDROID_ROOT"] = it }

        env.putAll(extraVars)

        return env.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    override fun defaultWorkingDirectory(): String {
        val home = File(homeDir)
        if (!home.exists()) home.mkdirs()
        return homeDir
    }
}
