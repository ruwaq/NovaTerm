package com.novaterm.core.session.manager

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.system.Os
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    companion object {
        private const val TAG = "AndroidShellProvider"
        /** Must match PreferencesRepository.SECURE_PREFS_NAME */
        private const val SECURE_PREFS_NAME = "novaterm_secure_prefs"
        private const val SECURE_PREFS_FALLBACK = "novaterm_secure_fallback"
    }

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

        // AI API keys — read from EncryptedSharedPreferences, export as env vars
        // so CLI tools (claude, gemini, aider, opencode) pick them up automatically.
        // Keys are encrypted with AES-256-GCM via Android Keystore.
        // If secure prefs are unavailable (Keystore failure), keys are skipped entirely.
        try {
            val apiPrefs = securePrefs ?: run {
                Log.w("NovaTerm", "Skipping API key export — encrypted prefs unavailable")
                null
            }
            apiPrefs?.getString("api_key_anthropic", null)?.takeIf { it.isNotEmpty() }?.let {
                env["ANTHROPIC_API_KEY"] = it
            }
            apiPrefs?.getString("api_key_google", null)?.takeIf { it.isNotEmpty() }?.let {
                env["GOOGLE_API_KEY"] = it
                env["GEMINI_API_KEY"] = it
            }
            apiPrefs?.getString("api_key_openai", null)?.takeIf { it.isNotEmpty() }?.let {
                env["OPENAI_API_KEY"] = it
            }
            apiPrefs?.getString("api_key_openrouter", null)?.takeIf { it.isNotEmpty() }?.let {
                env["OPENROUTER_API_KEY"] = it
            }
        } catch (e: Exception) {
            Log.w("NovaTerm", "Failed to load API keys from secure preferences", e)
        }

        env.putAll(extraVars)

        return env.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    /**
     * EncryptedSharedPreferences for API keys.
     *
     * Returns null if the Android Keystore is unavailable — in that case
     * API keys are NOT loaded rather than falling back to plaintext storage.
     * Cached via `lazy` — MasterKey.Builder involves crypto initialization.
     */
    private val securePrefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Do NOT fall back to plaintext — API keys must stay encrypted.
            Log.e("NovaTerm", "EncryptedSharedPreferences unavailable, API keys skipped", e)
            null
        }
    }


    override fun defaultWorkingDirectory(): String {
        val home = File(homeDir)
        val tmpdir = File("$prefix/tmp")
        val firstRun = !home.isDirectory
        if (firstRun) home.mkdirs()
        if (!tmpdir.isDirectory) tmpdir.mkdirs()

        // Create /tmp symlink pointing to $PREFIX/tmp.
        // Many tools (Claude Code, npm, Python) hardcode /tmp.
        // The app process has write access to its own data dir.
        try {
            val tmpLink = File(rootDir, "tmp")
            if (!tmpLink.exists()) {
                Os.symlink(tmpdir.absolutePath, tmpLink.absolutePath)
                Log.i("NovaTerm", "Created /tmp symlink → ${tmpdir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w("NovaTerm", "Could not create /tmp symlink (non-fatal)", e)
        }

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
        // Static shell configs — loaded from assets, written once on first run.
        // Only .motd is generated dynamically (uses Build.MODEL + appVersion).
        writeShellAsset("profile.sh", File(home, ".profile"), mapOf("VERSION" to appVersion))
        writeShellAsset("bashrc.sh",  File(home, ".bashrc"))
        writeShellAsset("inputrc",    File(home, ".inputrc"))
        writeShellAsset("shrc.sh",    File(home, ".shrc"))

        // ── .motd (dynamic: device name + app version) ──────────
        val motd = File(home, ".motd")
        if (!motd.exists()) {
            val device = Build.MODEL
            val androidVer = Build.VERSION.RELEASE
            val novaText = "NovaTerm v$appVersion"
            val deviceText = "$device · Android $androidVer"
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
    }

    /**
     * Write an asset from assets/shell/ to [dest], skipping if the file already exists.
     * Replaces {{KEY}} placeholders with [vars] values.
     */
    private fun writeShellAsset(assetName: String, dest: File, vars: Map<String, String> = emptyMap()) {
        if (dest.exists()) return
        try {
            var content = context.assets.open("shell/$assetName").bufferedReader().readText()
            for ((key, value) in vars) {
                content = content.replace("{{$key}}", value)
            }
            dest.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write shell asset $assetName", e)
        }
    }
}
