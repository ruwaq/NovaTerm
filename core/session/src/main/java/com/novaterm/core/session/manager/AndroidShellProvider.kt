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
    private val appVersion: String = "0.1.0",
) : ShellProvider {

    // rootDir = context.filesDir = /data/data/com.nvterm/files
    // This matches Termux layout: /data/data/com.termux/files/{usr,home}
    private val rootDir: String
        get() = context.filesDir.absolutePath

    private val prefix: String get() = "$rootDir/usr"
    private val homeDir: String get() = "$rootDir/home"

    // The system linker executes binaries from /data/ on Android 10+
    // where W^X (Write XOR Execute) blocks direct execution.
    private val systemLinker: String = if (File("/system/bin/linker64").exists()) {
        "/system/bin/linker64"
    } else {
        "/system/bin/linker"
    }

    override fun findShell(): String {
        // Check if shell binary EXISTS (not canExecute — W^X blocks that)
        val candidates = listOf(
            "$prefix/bin/login", // Termux login script: sets LD_PRELOAD, detects shell, exec bash
            "$prefix/bin/bash",
            "$prefix/bin/sh",
        )
        val shell = candidates.firstOrNull { File(it).exists() }
        if (shell != null) return shell
        return "/system/bin/sh"
    }

    /**
     * Returns the executable and arguments to launch the shell.
     * On Android 10+, routes through the system linker to bypass W^X.
     *
     * The login script is critical: it sets LD_PRELOAD with termux-exec
     * which intercepts ALL child exec() calls and routes them through
     * linker64 too. Without this, only the initial shell works but
     * commands like ls, apt, git all fail with "Permission denied".
     */
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
        val sh = "$prefix/bin/sh" // dash — can interpret the login script

        return if (File(loginScript).exists() && File(sh).exists()) {
            // Best path: linker64 runs dash, dash interprets login script,
            // login sets LD_PRELOAD and exec's bash
            arrayOf(systemLinker, sh, loginScript)
        } else if (File(shell).exists()) {
            // Fallback: run shell directly via linker (no LD_PRELOAD for children)
            arrayOf(systemLinker, shell, "--login")
        } else {
            arrayOf("/system/bin/sh")
        }
    }

    override fun buildEnvironment(extraVars: Map<String, String>): Array<String> {
        val env = LinkedHashMap<String, String>()

        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["HOME"] = homeDir
        env["PREFIX"] = prefix
        env["LANG"] = "en_US.UTF-8"
        env["PATH"] = "$homeDir/.local/bin:$prefix/bin:$prefix/bin/applets:/system/bin"
        // TMPDIR must be set BEFORE any child process starts.
        // Claude Code and Node.js use os.tmpdir() which reads TMPDIR.
        // Android's /tmp is owned by shell:shell and not writable by apps.
        env["TMPDIR"] = "$prefix/tmp"
        // Also set TMP and TEMP for compatibility with various tools
        env["TMP"] = "$prefix/tmp"
        env["TEMP"] = "$prefix/tmp"
        env["SHELL"] = findShell()
        env["LD_LIBRARY_PATH"] = "$prefix/lib"
        env["ENV"] = "$homeDir/.shrc"
        env["PWD"] = homeDir
        env["OLDPWD"] = homeDir
        env["SHLVL"] = "1"
        env["USER"] = "shell"
        env["LOGNAME"] = "shell"
        env["XDG_CONFIG_HOME"] = "$homeDir/.config"
        env["XDG_DATA_HOME"] = "$homeDir/.local/share"
        env["XDG_STATE_HOME"] = "$homeDir/.local/state"
        env["XDG_CACHE_HOME"] = "$homeDir/.cache"
        env["XDG_RUNTIME_DIR"] = "$prefix/tmp"

        // Terminal identification (AI tools check these)
        env["TERM_PROGRAM"] = "novaterm"
        env["TERM_PROGRAM_VERSION"] = appVersion

        // AI coding tools — sensible defaults for touch-first terminal
        env["CLAUDE_CODE_SCROLL_SPEED"] = "3"   // Touch-optimized scrolling
        env["AIDER_DARK_MODE"] = "true"         // Matches NovaTerm's default Gruvbox dark theme

        // Terminal dimensions: many CLI tools read LINES/COLUMNS before
        // bash's checkwinsize can set them. Provide sensible defaults so
        // tools like less, man, fzf, and column work correctly on launch.
        // The actual size is set by the PTY ioctl (TIOCSWINSZ) and bash
        // updates these via checkwinsize after the first command.
        env["LINES"] = extraVars.getOrDefault("LINES", "40")
        env["COLUMNS"] = extraVars.getOrDefault("COLUMNS", "120")

        // W^X bypass: termux-exec intercepts exec() calls and routes them
        // through /system/bin/linker64, which has permission to execute
        // binaries from the app's data directory. Required on Android 10+.
        // termux-exec LD_PRELOAD for W^X bypass: intercepts exec() calls
        // and routes through system linker. Linker variant for Android 10+.
        // W^X bypass: LD_PRELOAD with termux-exec.
        // We check multiple paths because context.filesDir may resolve as
        // /data/data/ or /data/user/0/ depending on Android version.
        // The bootstrap always extracts to context.filesDir/usr/lib/.
        val ldPreloadNames = listOf(
            "libtermux-exec-linker-ld-preload.so",
            "libtermux-exec-ld-preload.so",
            "libtermux-exec.so",
        )
        // Build candidate paths using BOTH possible base directories
        val dataDir = context.applicationInfo.dataDir  // /data/user/0/com.nvterm
        val legacyDir = "/data/data/${context.packageName}"  // /data/data/com.nvterm
        val candidatePaths = ldPreloadNames.flatMap { name ->
            listOf(
                "$prefix/lib/$name",
                "$dataDir/files/usr/lib/$name",
                "$legacyDir/files/usr/lib/$name",
            )
        }

        val ldPreloadLib = candidatePaths.firstOrNull { path ->
            try { File(path).exists() } catch (_: Exception) { false }
        }
        if (ldPreloadLib != null) {
            env["LD_PRELOAD"] = ldPreloadLib
            android.util.Log.i("NovaTerm", "LD_PRELOAD: $ldPreloadLib")
        } else {
            // Last resort: set it anyway using the canonical path — if the file
            // is there, the dynamic linker will find it even if Java can't stat it
            val fallback = "$prefix/lib/libtermux-exec-linker-ld-preload.so"
            env["LD_PRELOAD"] = fallback
            android.util.Log.w("NovaTerm", "LD_PRELOAD forced (File.exists failed): $fallback")
        }

        // NovaTerm environment — our own vars (preferred by future NovaTerm packages)
        env["NOVATERM__ROOTFS"] = rootDir
        env["NOVATERM__PREFIX"] = prefix
        env["NOVATERM_APP__DATA_DIR"] = context.applicationInfo.dataDir

        // Termux-exec compatibility — required by termux-exec LD_PRELOAD and
        // any Termux packages that read these vars at runtime.
        // Keep these until we have our own package repo with NovaTerm paths.
        env["TERMUX__ROOTFS"] = rootDir
        env["TERMUX__PREFIX"] = prefix
        env["TERMUX_APP__DATA_DIR"] = context.applicationInfo.dataDir
        env["TERMUX_APP__LEGACY_DATA_DIR"] = "/data/data/${context.packageName}"
        env["TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE"] = "enable"

        System.getenv("ANDROID_DATA")?.let { env["ANDROID_DATA"] = it }
            ?: run { env["ANDROID_DATA"] = "/data" }
        System.getenv("ANDROID_ROOT")?.let { env["ANDROID_ROOT"] = it }
            ?: run { env["ANDROID_ROOT"] = "/system" }

        // AI API keys — read from SharedPreferences, export as env vars
        // so CLI tools (claude, gemini, aider, opencode) pick them up automatically.
        // Keys are stored in app-private storage (Android sandbox protects them).
        try {
            val apiPrefs = context.getSharedPreferences("novaterm_prefs", Context.MODE_PRIVATE)
            apiPrefs.getString("api_key_anthropic", null)?.takeIf { it.isNotEmpty() }?.let {
                env["ANTHROPIC_API_KEY"] = it
            }
            apiPrefs.getString("api_key_google", null)?.takeIf { it.isNotEmpty() }?.let {
                env["GOOGLE_API_KEY"] = it
                env["GEMINI_API_KEY"] = it
            }
            apiPrefs.getString("api_key_openai", null)?.takeIf { it.isNotEmpty() }?.let {
                env["OPENAI_API_KEY"] = it
            }
            apiPrefs.getString("api_key_openrouter", null)?.takeIf { it.isNotEmpty() }?.let {
                env["OPENROUTER_API_KEY"] = it
            }
        } catch (e: Exception) {
            android.util.Log.w("NovaTerm", "Failed to load API keys from preferences", e)
        }

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
        val esc = "\u001b"

        // ── .profile (POSIX — sourced by any login shell) ────
        val profile = File(home, ".profile")
        if (!profile.exists()) {
            profile.writeText(buildString {
                appendLine("# NovaTerm shell profile (POSIX compatible)")
                appendLine("# Sourced on login shells. Keep this lightweight.")
                appendLine()
                appendLine("# ── Environment ────────────────────────────")
                appendLine("export EDITOR=vi")
                appendLine("export PAGER=less")
                appendLine("export LESS='-R -i -M'")
                appendLine("export COLORTERM=truecolor")
                appendLine("export TERM_PROGRAM=novaterm")
                appendLine("export TERM_PROGRAM_VERSION=\"$appVersion\"")
                appendLine()
                appendLine("# ── /tmp fix for Claude Code and other tools ──")
                appendLine("# Android doesn't have /tmp. Create it as symlink if possible,")
                appendLine("# or ensure TMPDIR points to our writable tmp directory.")
                appendLine("if [ ! -e /tmp ] && [ -w /data/data ]; then")
                appendLine("  ln -sf \"\$PREFIX/tmp\" /tmp 2>/dev/null")
                appendLine("fi")
                appendLine("if [ ! -d /tmp ]; then")
                appendLine("  mkdir -p \"\$PREFIX/tmp\" 2>/dev/null")
                appendLine("  export TMPDIR=\"\$PREFIX/tmp\"")
                appendLine("fi")
                appendLine()
                appendLine("# ── History (crash-safe: saved on every command) ──")
                appendLine("export HISTSIZE=10000")
                appendLine("export HISTFILESIZE=10000")
                appendLine("export HISTCONTROL=erasedups:ignoredups:ignorespace")
                appendLine("export HISTFILE=\"\$HOME/.local/state/bash_history\"")
                appendLine()
                appendLine("# ── Prompt (initial fallback) ──────────────")
                appendLine("# Interactive prompt is configured in .bashrc with OSC 133 markers.")
                appendLine("# This fallback covers non-bash POSIX shells.")
                appendLine("export PS1='${esc}[38;5;208m>${esc}[0m '")
                appendLine("shopt -s histappend 2>/dev/null")
                appendLine()
                appendLine("# Auto-install Claude Code on first launch (runs once)")
                appendLine("if [ -x \"\$PREFIX/bin/setup-claude\" ]; then")
                appendLine("  \"\$PREFIX/bin/setup-claude\" && rm -f \"\$PREFIX/bin/setup-claude\"")
                appendLine("fi")
                appendLine()
                appendLine("# Mark command start after profile loads (OSC 133)")
                appendLine("printf '${esc}]133;B\\007'")
                appendLine()
            })
        }

        // ── .bashrc (bash-specific interactive config) ────────
        val bashrc = File(home, ".bashrc")
        if (!bashrc.exists()) {
            bashrc.writeText(buildString {
                appendLine("# NovaTerm bash configuration")
                appendLine("# Sourced for interactive non-login shells.")
                appendLine()
                appendLine("# Source profile if not already loaded")
                appendLine("[ -z \"\$HISTFILE\" ] && . \"\$HOME/.profile\"")
                appendLine()
                appendLine("# ── Shell options (bash only) ──────────────")
                appendLine("if [ -n \"\$BASH_VERSION\" ]; then")
                appendLine("  shopt -s checkwinsize   # Update LINES/COLUMNS after each command")
                appendLine("  shopt -s histappend     # Append to history, don't overwrite")
                appendLine("  shopt -s cmdhist        # Save multi-line commands as one entry")
                appendLine("  shopt -s globstar 2>/dev/null  # ** recursive glob (bash 4+)")
                appendLine("  shopt -s nocaseglob     # Case-insensitive globbing")
                appendLine("fi")
                appendLine()
                appendLine("# ── Colors ─────────────────────────────────")
                appendLine("alias ls='ls --color=auto'")
                appendLine("alias grep='grep --color=auto'")
                appendLine("alias diff='diff --color=auto'")
                appendLine()
                appendLine("# ── Navigation ─────────────────────────────")
                appendLine("alias ll='ls -lah'")
                appendLine("alias la='ls -A'")
                appendLine("alias ..='cd ..'")
                appendLine("alias ...='cd ../..'")
                appendLine("alias p='cd ~/projects'")
                appendLine("alias s='cd ~/storage/shared'")
                appendLine()
                appendLine("# ── Safety ─────────────────────────────────")
                appendLine("alias rm='rm -i'")
                appendLine("alias mv='mv -i'")
                appendLine("alias cp='cp -i'")
                appendLine()
                appendLine("# ── Shortcuts ──────────────────────────────")
                appendLine("alias h='history'")
                appendLine("alias c='clear'")
                appendLine("alias q='exit'")
                appendLine()
                appendLine("# ── Network ────────────────────────────────")
                appendLine("alias ports='netstat -tlnp 2>/dev/null || ss -tlnp'")
                appendLine("alias myip='curl -s ifconfig.me'")
                appendLine("alias timer='echo \"Timer started. Stop with Ctrl+D.\" && date && time cat && date'")
                appendLine()
                appendLine("# ── Functions ──────────────────────────────")
                appendLine("mkcd() { mkdir -p \"\$1\" && cd \"\$1\"; }")
                appendLine()
                appendLine("# Universal archive extraction")
                appendLine("extract() {")
                appendLine("  if [ -f \"\$1\" ]; then")
                appendLine("    case \"\$1\" in")
                appendLine("      *.tar.bz2) tar xjf \"\$1\" ;;")
                appendLine("      *.tar.gz)  tar xzf \"\$1\" ;;")
                appendLine("      *.tar.xz)  tar xJf \"\$1\" ;;")
                appendLine("      *.tar.zst) tar --zstd -xf \"\$1\" ;;")
                appendLine("      *.bz2)     bunzip2 \"\$1\" ;;")
                appendLine("      *.gz)      gunzip \"\$1\" ;;")
                appendLine("      *.tar)     tar xf \"\$1\" ;;")
                appendLine("      *.tbz2)    tar xjf \"\$1\" ;;")
                appendLine("      *.tgz)     tar xzf \"\$1\" ;;")
                appendLine("      *.zip)     unzip \"\$1\" ;;")
                appendLine("      *.7z)      7z x \"\$1\" ;;")
                appendLine("      *.xz)      unxz \"\$1\" ;;")
                appendLine("      *.zst)     unzstd \"\$1\" ;;")
                appendLine("      *)         printf 'Cannot extract: %s\\n' \"\$1\" >&2; return 1 ;;")
                appendLine("    esac")
                appendLine("  else")
                appendLine("    printf 'File not found: %s\\n' \"\$1\" >&2; return 1")
                appendLine("  fi")
                appendLine("}")
                appendLine()
                appendLine("# One-liner weather")
                appendLine("weather() { curl -s \"wttr.in/\${1:-}?format=3\"; }")
                appendLine()
                appendLine("# Cheatsheets from cheat.sh")
                appendLine("cheat() { curl -s \"cheat.sh/\$1\"; }")
                appendLine()
                appendLine("# ── Shell integration (OSC 133 semantic markers) ──")
                appendLine("# Enables command tracking, completion notifications, and AI block parsing.")
                appendLine("_novaterm_prompt() {")
                appendLine("  local exit=\$?")
                appendLine("  # Mark end of previous command output + exit code")
                appendLine("  printf '${esc}]133;D;%d\\007' \"\$exit\"")
                appendLine("  # Mark prompt start")
                appendLine("  printf '${esc}]133;A\\007'")
                appendLine("  # Original prompt logic: orange > on success, red > on error")
                appendLine("  if [ \$exit -eq 0 ]; then")
                appendLine("    PS1='${esc}[38;5;208m>${esc}[0m '")
                appendLine("  else")
                appendLine("    PS1='${esc}[38;5;167m>${esc}[0m '")
                appendLine("  fi")
                appendLine("}")
                appendLine()
                appendLine("# Wire up prompt and crash-safe history")
                appendLine("PROMPT_COMMAND=\"_novaterm_prompt;history -a\"")
                appendLine()
                appendLine("# PS0 triggers on command execution (bash 4.4+) — acts as preexec.")
                appendLine("# Marks the start of command output (OSC 133;C).")
                appendLine("if [ -n \"\$BASH_VERSION\" ]; then")
                appendLine("  if [ \"\${BASH_VERSINFO[0]}\" -ge 5 ] || { [ \"\${BASH_VERSINFO[0]}\" -eq 4 ] && [ \"\${BASH_VERSINFO[1]}\" -ge 4 ]; }; then")
                appendLine("    PS0='${esc}]133;C\\007'")
                appendLine("  fi")
                appendLine("fi")
                appendLine()
                appendLine("# ── Command not found ──────────────────────")
                appendLine("command_not_found_handler() {")
                appendLine("  local pkg")
                appendLine("  printf '\\e[38;5;167m%s\\e[0m: command not found\\n' \"\$1\" >&2")
                appendLine("  pkg=\$(apt-cache search --names-only \"^\$1\$\" 2>/dev/null | head -1 | cut -d' ' -f1)")
                appendLine("  if [ -n \"\$pkg\" ]; then")
                appendLine("    printf '  \\e[38;5;208m→\\e[0m Install with: \\e[1mpkg install %s\\e[0m\\n' \"\$pkg\" >&2")
                appendLine("  fi")
                appendLine("  return 127")
                appendLine("}")
                appendLine()
            })
        }

        // Welcome banner — informative about the environment
        val motd = File(home, ".motd")
        if (!motd.exists()) {
            val device = Build.MODEL
            val androidVer = Build.VERSION.RELEASE
            val novaText = "NovaTerm v$appVersion"
            val deviceText = "$device · Android $androidVer"
            // Box width adapts to longest content line (2 leading + 2 trailing spaces inside)
            val innerWidth = maxOf(novaText.length, deviceText.length) + 4
            val border = "─".repeat(innerWidth)
            val novaPad = " ".repeat((innerWidth - 2 - novaText.length).coerceAtLeast(0))
            val devicePad = " ".repeat((innerWidth - 2 - deviceText.length).coerceAtLeast(0))
            motd.writeText(buildString {
                appendLine()
                appendLine("\u001b[38;5;208m  ╭${border}╮\u001b[0m")
                appendLine("\u001b[38;5;208m  │\u001b[0m  \u001b[1m$novaText\u001b[0m${novaPad}\u001b[38;5;208m│\u001b[0m")
                appendLine("\u001b[38;5;208m  │\u001b[0m  \u001b[38;5;246m$deviceText\u001b[0m${devicePad}\u001b[38;5;208m│\u001b[0m")
                appendLine("\u001b[38;5;208m  ╰${border}╯\u001b[0m")
                appendLine()
                appendLine("\u001b[38;5;246m  ~/projects/\u001b[0m    \u001b[38;5;242m← your workspace\u001b[0m")
                appendLine("\u001b[38;5;246m  ~/storage/\u001b[0m     \u001b[38;5;242m← phone files\u001b[0m")
                appendLine("\u001b[38;5;246m  ~/.config/\u001b[0m     \u001b[38;5;242m← app configs\u001b[0m")
                appendLine()
            })
        }

        // ── .inputrc (readline configuration) ─────────────
        val inputrc = File(home, ".inputrc")
        if (!inputrc.exists()) {
            inputrc.writeText(buildString {
                appendLine("# NovaTerm readline configuration")
                appendLine()
                appendLine("# Case-insensitive tab completion")
                appendLine("set completion-ignore-case on")
                appendLine()
                appendLine("# Show all matches if ambiguous")
                appendLine("set show-all-if-ambiguous on")
                appendLine("set show-all-if-unmodified on")
                appendLine()
                appendLine("# Color completion by file type")
                appendLine("set colored-stats on")
                appendLine("set colored-completion-prefix on")
                appendLine()
                appendLine("# Don't ring bell on tab completion")
                appendLine("set bell-style none")
                appendLine()
                appendLine("# Word navigation with Ctrl+Left/Right")
                appendLine("\"\\e[1;5D\": backward-word")
                appendLine("\"\\e[1;5C\": forward-word")
                appendLine()
                appendLine("# Up/Down search history by prefix")
                appendLine("\"\\e[A\": history-search-backward")
                appendLine("\"\\e[B\": history-search-forward")
                appendLine()
                appendLine("# Append slash to symlinked directories")
                appendLine("set mark-symlinked-directories on")
                appendLine()
                appendLine("# Append file type indicator (like ls -F)")
                appendLine("set visible-stats on")
                appendLine()
                appendLine("# Don't page completions")
                appendLine("set page-completions off")
                appendLine()
                appendLine("# Delete key and Ctrl+W")
                appendLine("\"\\e[3~\": delete-char")
                appendLine("\"\\C-w\": backward-kill-word")
                appendLine()
            })
        }

        // Shell init: source configs and display MOTD.
        // ENV=$HOME/.shrc is set in buildEnvironment(), so this runs
        // for any POSIX shell (sh, bash, dash) on startup.
        val shrc = File(home, ".shrc")
        if (!shrc.exists()) {
            shrc.writeText(buildString {
                appendLine("# NovaTerm shell init (sourced via ENV variable)")
                appendLine("# NOTE: Only use shell builtins here — external commands")
                appendLine("# may not work before LD_PRELOAD/termux-exec is active.")
                appendLine("[ -f \"\$HOME/.profile\" ] && . \"\$HOME/.profile\"")
                appendLine("[ -f \"\$HOME/.bashrc\" ] && . \"\$HOME/.bashrc\"")
                appendLine("# Display MOTD using read loop (no cat — W^X safe)")
                appendLine("if [ -f \"\$HOME/.motd\" ]; then")
                appendLine("  while IFS= read -r line; do echo \"\$line\"; done < \"\$HOME/.motd\"")
                appendLine("fi")
                appendLine()
                appendLine("# Mark initial prompt (OSC 133)")
                appendLine("printf '${esc}]133;A\\007'")
                appendLine()
            })
        }
    }
}
