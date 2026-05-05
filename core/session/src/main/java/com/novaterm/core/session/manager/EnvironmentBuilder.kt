package com.novaterm.core.session.manager

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Builds the environment variable array for terminal sessions.
 *
 * Handles core terminal vars, XDG dirs, W^X bypass
 * (LD_PRELOAD), and NovaTerm compat vars.
 */
class EnvironmentBuilder(
    private val context: Context,
    private val appVersion: String,
    private val prefix: String,
    private val homeDir: String,
    private val rootDir: String,
    private val shellFinder: () -> String,
) {
    fun buildEnvironment(extraVars: Map<String, String>): Array<String> {
        val env = LinkedHashMap<String, String>()

        // Core terminal vars
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["HOME"] = homeDir
        env["PREFIX"] = prefix
        env["LANG"] = "en_US.UTF-8"
        env["PATH"] = "$homeDir/.local/bin:$prefix/bin:$prefix/bin/applets:/system/bin"
        env["TMPDIR"] = "$prefix/tmp"
        env["TMP"] = "$prefix/tmp"
        env["TEMP"] = "$prefix/tmp"
        env["SHELL"] = shellFinder()
        env["LD_LIBRARY_PATH"] = "$prefix/lib"
        env["ENV"] = "$homeDir/.shrc"
        env["PWD"] = homeDir
        env["OLDPWD"] = homeDir
        env["SHLVL"] = "1"
        env["USER"] = "shell"
        env["LOGNAME"] = "shell"

        // XDG directories
        env["XDG_CONFIG_HOME"] = "$homeDir/.config"
        env["XDG_DATA_HOME"] = "$homeDir/.local/share"
        env["XDG_STATE_HOME"] = "$homeDir/.local/state"
        env["XDG_CACHE_HOME"] = "$homeDir/.cache"
        env["XDG_RUNTIME_DIR"] = "$prefix/tmp"

        // Terminal identification
        env["TERM_PROGRAM"] = "novaterm"
        env["TERM_PROGRAM_VERSION"] = appVersion

        // Terminal dimensions
        env["LINES"] = extraVars.getOrDefault("LINES", "40")
        env["COLUMNS"] = extraVars.getOrDefault("COLUMNS", "120")

        // W^X bypass: LD_PRELOAD with nvterm-exec
        setupLdPreload(env)

        // NovaTerm environment
        env["NOVATERM__ROOTFS"] = rootDir
        env["NOVATERM__PREFIX"] = prefix
        env["NOVATERM_APP__DATA_DIR"] = context.applicationInfo.dataDir

        // Termux compat: legacy env vars for packages that reference TERMUX__*
        env["TERMUX__ROOTFS"] = rootDir
        env["TERMUX__PREFIX"] = prefix
        env["TERMUX_APP__DATA_DIR"] = context.applicationInfo.dataDir
        env["TERMUX_APP__LEGACY_DATA_DIR"] = "/data/data/${context.packageName}"
        env["TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE"] = "enable"

        // Android system vars
        System.getenv("ANDROID_DATA")?.let { env["ANDROID_DATA"] = it }
            ?: run { env["ANDROID_DATA"] = "/data" }
        System.getenv("ANDROID_ROOT")?.let { env["ANDROID_ROOT"] = it }
            ?: run { env["ANDROID_ROOT"] = "/system" }

        env.putAll(extraVars)
        return env.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    private fun setupLdPreload(env: MutableMap<String, String>) {
        // Search for nvterm-exec (preferred) first, then fallback to termux-exec (compat).
        // NOTE: libnvterm-core is a regular shared library (dependency of nvterm-exec),
        // NOT an LD_PRELOAD library. Only nvterm-exec-* libs go in LD_PRELOAD.
        val ldPreloadNames = listOf(
            "libnvterm-exec-linker-ld-preload.so",
            "libnvterm-exec-ld-preload.so",
            "libnvterm-exec.so",
            // Legacy names from Termux bootstrap (renamed during extraction)
            "libtermux-exec-linker-ld-preload.so",
            "libtermux-exec-ld-preload.so",
            "libtermux-exec.so",
        )
        val dataDir = context.applicationInfo.dataDir
        val legacyDir = "/data/data/${context.packageName}"
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
            Log.i("NovaTerm", "LD_PRELOAD: $ldPreloadLib")
        } else {
            val fallback = "$prefix/lib/libnvterm-exec-linker-ld-preload.so"
            env["LD_PRELOAD"] = fallback
            Log.w("NovaTerm", "LD_PRELOAD forced (File.exists failed): $fallback")
        }
    }

}
