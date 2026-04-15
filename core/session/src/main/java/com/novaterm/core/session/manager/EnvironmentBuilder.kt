package com.novaterm.core.session.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Builds the environment variable array for terminal sessions.
 *
 * Handles core terminal vars, XDG dirs, AI tool hints, W^X bypass
 * (LD_PRELOAD), NovaTerm/Termux compat vars, and API key export
 * from EncryptedSharedPreferences.
 */
class EnvironmentBuilder(
    private val context: Context,
    private val appVersion: String,
    private val prefix: String,
    private val homeDir: String,
    private val rootDir: String,
    private val shellFinder: () -> String,
) {
    /**
     * EncryptedSharedPreferences for API keys.
     * Returns null if Android Keystore is unavailable.
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
            Log.e("NovaTerm", "EncryptedSharedPreferences unavailable, API keys skipped", e)
            null
        }
    }

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

        // AI coding tools defaults
        env["CLAUDE_CODE_SCROLL_SPEED"] = "3"
        env["AIDER_DARK_MODE"] = "true"

        // Terminal dimensions
        env["LINES"] = extraVars.getOrDefault("LINES", "40")
        env["COLUMNS"] = extraVars.getOrDefault("COLUMNS", "120")

        // W^X bypass: LD_PRELOAD with termux-exec
        setupLdPreload(env)

        // NovaTerm environment
        env["NOVATERM__ROOTFS"] = rootDir
        env["NOVATERM__PREFIX"] = prefix
        env["NOVATERM_APP__DATA_DIR"] = context.applicationInfo.dataDir

        // Termux-exec compatibility
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

        // API keys from secure storage
        exportApiKeys(env)

        env.putAll(extraVars)
        return env.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    private fun setupLdPreload(env: MutableMap<String, String>) {
        // Search for nvterm-exec (preferred) first, then fallback to termux-exec (compat)
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

    private fun exportApiKeys(env: MutableMap<String, String>) {
        try {
            val apiPrefs = securePrefs ?: run {
                Log.w("NovaTerm", "Skipping API key export — encrypted prefs unavailable")
                return
            }

            // Security note: API keys are exported to terminal environment
            // This is intentional for AI integration but has security implications:
            // 1. Keys may be exposed in shell history
            // 2. Keys may be captured by malicious processes running in the same session
            // 3. Keys are visible to any user who gains access to the terminal
            // Consider using session-specific tokens or restricting key export to trusted sessions

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
            Log.w("NovaTerm", "Failed to load API keys from secure preferences", e)
        }
    }

    companion object {
        private const val SECURE_PREFS_NAME = "novaterm_secure_prefs"
    }
}
