package com.novaterm.core.session.manager

import android.content.Context
import android.os.Build
import android.os.Environment
import com.novaterm.core.common.contract.ShellProvider
import java.io.File

/**
 * Locates shell binaries and constructs environment for terminal sessions.
 * Searches the app's private prefix first, then falls back to system shell.
 * Handles first-run bootstrapping: creates home directory structure,
 * standard project workspace, storage symlinks, and shell config files.
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
        env["PATH"] = "$homeDir/.local/bin:$prefix/bin:$prefix/bin/applets:/system/bin"
        env["TMPDIR"] = "$prefix/tmp"
        env["SHELL"] = findShell()
        env["LD_LIBRARY_PATH"] = "$prefix/lib"
        env["ENV"] = "$homeDir/.shrc"
        env["XDG_CONFIG_HOME"] = "$homeDir/.config"
        env["XDG_DATA_HOME"] = "$homeDir/.local/share"
        env["XDG_STATE_HOME"] = "$homeDir/.local/state"
        env["XDG_CACHE_HOME"] = "$homeDir/.cache"
        env["XDG_RUNTIME_DIR"] = "$prefix/tmp"

        // Terminal identification (AI tools check these)
        env["TERM_PROGRAM"] = "novaterm"
        env["TERM_PROGRAM_VERSION"] = "0.1.0"

        // W^X bypass: termux-exec intercepts exec() calls and routes them
        // through /system/bin/linker64, which has permission to execute
        // binaries from the app's data directory. Required on Android 10+.
        val termuxExecLib = "$prefix/lib/libtermux-exec.so"
        if (java.io.File(termuxExecLib).exists()) {
            env["LD_PRELOAD"] = termuxExecLib
        }

        // Termux-exec environment (tells termux-exec where the app lives)
        env["TERMUX__ROOTFS"] = rootDir
        env["TERMUX__PREFIX"] = prefix
        env["TERMUX_APP__DATA_DIR"] = context.applicationInfo.dataDir

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
            setupDirectoryStructure(home)
            setupStorageLinks(home)
            setupShellConfig(home)
        }

        return homeDir
    }

    // ── First-run: directory structure ─────────────────────

    private fun setupDirectoryStructure(home: File) {
        // Standard workspace
        File(home, "projects").mkdirs()

        // XDG directories (freedesktop.org Base Directory Specification)
        File(home, ".config").mkdirs()
        File(home, ".local/bin").mkdirs()
        File(home, ".local/share").mkdirs()
        File(home, ".local/state").mkdirs()
        File(home, ".cache").mkdirs()

        // SSH directory with correct permissions
        val sshDir = File(home, ".ssh")
        sshDir.mkdirs()
        sshDir.setReadable(false, false)
        sshDir.setReadable(true, true)
        sshDir.setWritable(false, false)
        sshDir.setWritable(true, true)
        sshDir.setExecutable(false, false)
        sshDir.setExecutable(true, true) // 0700
    }

    // ── First-run: storage symlinks ───────────────────────

    private fun setupStorageLinks(home: File) {
        val storageDir = File(home, "storage")
        if (storageDir.exists()) return
        storageDir.mkdirs()

        val sdcard = Environment.getExternalStorageDirectory().absolutePath

        // Only create symlinks to directories that exist and are accessible
        mapOf(
            "shared" to sdcard,
            "downloads" to "$sdcard/Download",
            "documents" to "$sdcard/Documents",
            "dcim" to "$sdcard/DCIM",
            "pictures" to "$sdcard/Pictures",
            "music" to "$sdcard/Music",
        ).forEach { (name, target) ->
            try {
                val targetFile = File(target)
                if (targetFile.isDirectory) {
                    val link = File(storageDir, name)
                    if (!link.exists()) {
                        android.system.Os.symlink(target, link.absolutePath)
                    }
                }
            } catch (_: Exception) {
                // Storage may not be accessible yet — user can run setup-storage later
            }
        }
    }

    // ── First-run: shell configuration ────────────────────

    private fun setupShellConfig(home: File) {
        // PS1 with real ESC characters — works in any POSIX shell.
        val profile = File(home, ".profile")
        if (!profile.exists()) {
            profile.writeText(buildString {
                appendLine("# NovaTerm shell profile")
                appendLine()
                appendLine("# Prompt")
                appendLine("export PS1='\u001b[38;5;208m>\u001b[0m '")
                appendLine()
                appendLine("# History — saved immediately so it survives crashes")
                appendLine("export HISTSIZE=10000")
                appendLine("export HISTFILESIZE=10000")
                appendLine("export HISTCONTROL=erasedups:ignoredups:ignorespace")
                appendLine("export HISTFILE=\"\$HOME/.local/state/bash_history\"")
                appendLine("PROMPT_COMMAND=\"history -a;\$PROMPT_COMMAND\"")
                appendLine("shopt -s histappend 2>/dev/null")
                appendLine()
                appendLine("# Editor")
                appendLine("export EDITOR=vi")
                appendLine()
                appendLine("# Aliases")
                appendLine("alias ll='ls -la'")
                appendLine("alias la='ls -A'")
                appendLine("alias p='cd ~/projects'")
                appendLine("alias s='cd ~/storage/shared'")
                appendLine()
            })
        }

        // Welcome banner — informative about the environment
        val motd = File(home, ".motd")
        if (!motd.exists()) {
            val device = Build.MODEL
            val androidVer = Build.VERSION.RELEASE
            motd.writeText(buildString {
                appendLine()
                appendLine("\u001b[38;5;208m  ╭───────────────────────────────╮\u001b[0m")
                appendLine("\u001b[38;5;208m  │\u001b[0m  \u001b[1mNovaTerm\u001b[0m v0.1.0              \u001b[38;5;208m│\u001b[0m")
                appendLine("\u001b[38;5;208m  │\u001b[0m  \u001b[38;5;246m$device · Android $androidVer\u001b[0m${" ".repeat((19 - device.length - androidVer.length).coerceAtLeast(0))}\u001b[38;5;208m│\u001b[0m")
                appendLine("\u001b[38;5;208m  ╰───────────────────────────────╯\u001b[0m")
                appendLine()
                appendLine("\u001b[38;5;246m  ~/projects/\u001b[0m    \u001b[38;5;242m← your workspace\u001b[0m")
                appendLine("\u001b[38;5;246m  ~/storage/\u001b[0m     \u001b[38;5;242m← phone files\u001b[0m")
                appendLine("\u001b[38;5;246m  ~/.config/\u001b[0m     \u001b[38;5;242m← app configs\u001b[0m")
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
