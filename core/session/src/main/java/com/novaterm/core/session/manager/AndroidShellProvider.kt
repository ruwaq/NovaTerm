package com.novaterm.core.session.manager

import android.content.Context
import com.novaterm.core.common.contract.ShellProvider
import java.io.File

/**
 * Locates shell binaries and constructs environment for terminal sessions.
 * Searches the app's private prefix first, then falls back to system shell.
 * Handles first-run bootstrapping of home directory files (.profile, .motd, .shrc).
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
        env["PATH"] = "$prefix/bin:$prefix/bin/applets:/system/bin"
        env["TMPDIR"] = "$prefix/tmp"
        env["SHELL"] = findShell()
        env["LD_LIBRARY_PATH"] = "$prefix/lib"
        env["ENV"] = "$homeDir/.shrc"

        System.getenv("ANDROID_DATA")?.let { env["ANDROID_DATA"] = it }
            ?: run { env["ANDROID_DATA"] = "/data" }
        System.getenv("ANDROID_ROOT")?.let { env["ANDROID_ROOT"] = it }
            ?: run { env["ANDROID_ROOT"] = "/system" }

        env.putAll(extraVars)

        return env.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    override fun defaultWorkingDirectory(): String {
        val home = File(homeDir)
        val tmpdir = File("$prefix/tmp")
        val firstRun = !home.isDirectory
        if (firstRun) home.mkdirs()
        if (!tmpdir.isDirectory) tmpdir.mkdirs()

        if (firstRun) {
            setupFirstRun(home)
        }

        return homeDir
    }

    private fun setupFirstRun(home: File) {
        // PS1 with real ESC characters — works in any POSIX shell (sh/mksh/bash/zsh).
        // Bash-only \[...\] and \e escapes are NOT portable.
        val profile = File(home, ".profile")
        if (!profile.exists()) {
            profile.writeText(buildString {
                appendLine("# NovaTerm - short prompt")
                appendLine("export PS1='\u001b[38;5;208m>\u001b[0m '")
                appendLine()
            })
        }

        // Welcome banner displayed via cat in .shrc
        val motd = File(home, ".motd")
        if (!motd.exists()) {
            motd.writeText(buildString {
                appendLine()
                appendLine("\u001b[38;5;208m  ╭─────────────────────────────╮\u001b[0m")
                appendLine("\u001b[38;5;208m  │\u001b[0m  \u001b[1mNovaTerm\u001b[0m v0.1.0            \u001b[38;5;208m│\u001b[0m")
                appendLine("\u001b[38;5;208m  │\u001b[0m  Next-gen Android Terminal   \u001b[38;5;208m│\u001b[0m")
                appendLine("\u001b[38;5;208m  ╰─────────────────────────────╯\u001b[0m")
                appendLine()
                appendLine("\u001b[38;5;246m  Tips:\u001b[0m")
                appendLine("\u001b[38;5;246m  • Pinch to zoom text\u001b[0m")
                appendLine("\u001b[38;5;246m  • Long-press extra keys for popups\u001b[0m")
                appendLine("\u001b[38;5;246m  • Swipe from left edge for drawer\u001b[0m")
                appendLine("\u001b[38;5;246m  • + button to add sessions\u001b[0m")
                appendLine()
            })
        }

        // Shell init: source .profile, display MOTD
        val shrc = File(home, ".shrc")
        if (!shrc.exists()) {
            shrc.writeText(buildString {
                appendLine("# NovaTerm shell init")
                appendLine("[ -f \"\$HOME/.profile\" ] && . \"\$HOME/.profile\"")
                appendLine("[ -f \"\$HOME/.motd\" ] && cat \"\$HOME/.motd\"")
                appendLine()
            })
        }
    }
}
