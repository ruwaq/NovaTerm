package com.novaterm.feature.settings.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TerminalPreferences(
    val fontSize: Int = 12,  // 12sp default for 6.83" AMOLED (~55 cols)
    val fontFamily: String = FONT_FAMILY_SYSTEM_MONO,
    val keepScreenOn: Boolean = false,
    val hapticFeedback: Boolean = true,
    val bellEnabled: Boolean = true,
    val showExtraKeys: Boolean = true,
    val extraKeysStyle: String = EXTRA_KEYS_STYLE_DEFAULT,
    val backIsEscape: Boolean = true,
    val colorScheme: String = "gruvbox-dark",
    val useRustBackend: Boolean = false,
    val useGpuRenderer: Boolean = false,
    val pipOnLeave: Boolean = false,
    val sessionGroupingEnabled: Boolean = true,
    val mcpEnabled: Boolean = false,
    val mcpPort: Int = 8080, // Must match McpServerConfig.DEFAULT_PORT
    val llmEnabled: Boolean = false,
    val scrollbackLines: Int = DEFAULT_SCROLLBACK_LINES,
    val anthropicApiKey: String = "",
    val googleApiKey: String = "",
    val openaiApiKey: String = "",
    val openrouterApiKey: String = "",
) {
    companion object {
        const val EXTRA_KEYS_STYLE_DEFAULT = "default"
        const val EXTRA_KEYS_STYLE_VIM = "vim"
        const val EXTRA_KEYS_STYLE_DEV = "dev"
        const val EXTRA_KEYS_STYLE_MINIMAL = "minimal"
        const val EXTRA_KEYS_STYLE_AI = "ai"

        val EXTRA_KEYS_STYLES = listOf(
            EXTRA_KEYS_STYLE_DEFAULT,
            EXTRA_KEYS_STYLE_VIM,
            EXTRA_KEYS_STYLE_DEV,
            EXTRA_KEYS_STYLE_MINIMAL,
            EXTRA_KEYS_STYLE_AI,
        )

        const val DEFAULT_SCROLLBACK_LINES = 10000

        /** Allowed scrollback sizes for the settings dropdown. */
        val SCROLLBACK_OPTIONS = listOf(1000, 5000, 10000, 25000, 50000)

        const val FONT_FAMILY_SYSTEM_MONO = "system-mono"
        const val FONT_FAMILY_JETBRAINS_MONO = "jetbrains-mono"
        const val FONT_FAMILY_FIRA_CODE = "fira-code"
        const val FONT_FAMILY_SOURCE_CODE_PRO = "source-code-pro"
        const val FONT_FAMILY_CASCADIA = "cascadia"

        /** All supported font families with display names. */
        val FONT_FAMILIES = listOf(
            FONT_FAMILY_SYSTEM_MONO to "System Monospace",
            FONT_FAMILY_JETBRAINS_MONO to "JetBrains Mono",
            FONT_FAMILY_FIRA_CODE to "Fira Code",
            FONT_FAMILY_SOURCE_CODE_PRO to "Source Code Pro",
            FONT_FAMILY_CASCADIA to "Cascadia Code",
        )
    }
}

class PreferencesRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Separate EncryptedSharedPreferences instance for API keys only.
     * Non-sensitive settings stay in regular prefs (faster, no crypto overhead).
     * Falls back to regular prefs if crypto initialization fails
     * (e.g., corrupted Android Keystore, old device quirks).
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
            // No plaintext fallback — API keys silently unavailable if crypto fails.
            Log.e(TAG, "EncryptedSharedPreferences unavailable, API keys skipped", e)
            null
        }
    }

    companion object {
        const val PREFS_NAME = "novaterm_prefs"
        const val SECURE_PREFS_NAME = "novaterm_secure_prefs"
        private const val TAG = "PreferencesRepository"
        private const val KEY_MIGRATION_DONE = "api_keys_migrated_to_secure"

        private val API_KEY_NAMES = listOf(
            "api_key_anthropic",
            "api_key_google",
            "api_key_openai",
            "api_key_openrouter",
        )
    }

    private val _preferences = MutableStateFlow(load())
    val preferences: StateFlow<TerminalPreferences> = _preferences.asStateFlow()

    val isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }

    val isAiSetupCompleted: Boolean
        get() = prefs.getBoolean("ai_setup_completed", false)

    fun completeAiSetup() {
        prefs.edit().putBoolean("ai_setup_completed", true).apply()
    }

    @Synchronized
    fun update(newPrefs: TerminalPreferences) {
        _preferences.value = newPrefs
        save(newPrefs)
    }

    @Synchronized
    fun resetToDefaults() {
        val defaults = TerminalPreferences()
        _preferences.value = defaults
        save(defaults)
    }

    private fun load(): TerminalPreferences {
        migrateApiKeysToSecurePrefs()

        return TerminalPreferences(
            fontSize = prefs.getInt("font_size", 12),
            fontFamily = prefs.getString("font_family", TerminalPreferences.FONT_FAMILY_SYSTEM_MONO)
                ?: TerminalPreferences.FONT_FAMILY_SYSTEM_MONO,
            keepScreenOn = prefs.getBoolean("keep_screen_on", false),
            hapticFeedback = prefs.getBoolean("haptic_feedback", true),
            bellEnabled = prefs.getBoolean("bell_enabled", true),
            showExtraKeys = prefs.getBoolean("show_extra_keys", true),
            extraKeysStyle = prefs.getString("extra_keys_style", TerminalPreferences.EXTRA_KEYS_STYLE_DEFAULT)
                ?: TerminalPreferences.EXTRA_KEYS_STYLE_DEFAULT,
            backIsEscape = prefs.getBoolean("back_is_escape", true),
            colorScheme = prefs.getString("color_scheme", "gruvbox-dark") ?: "gruvbox-dark",
            useRustBackend = prefs.getBoolean("use_rust_backend", false),
            useGpuRenderer = prefs.getBoolean("use_gpu_renderer", false),
            pipOnLeave = prefs.getBoolean("pip_on_leave", false),
            sessionGroupingEnabled = prefs.getBoolean("session_grouping_enabled", true),
            mcpEnabled = prefs.getBoolean("mcp_enabled", false),
            mcpPort = prefs.getInt("mcp_port", 8080), // Must match McpServerConfig.DEFAULT_PORT
            llmEnabled = prefs.getBoolean("llm_enabled", false),
            scrollbackLines = prefs.getInt("scrollback_lines", TerminalPreferences.DEFAULT_SCROLLBACK_LINES),
            anthropicApiKey = securePrefs?.getString("api_key_anthropic", "") ?: "",
            googleApiKey = securePrefs?.getString("api_key_google", "") ?: "",
            openaiApiKey = securePrefs?.getString("api_key_openai", "") ?: "",
            openrouterApiKey = securePrefs?.getString("api_key_openrouter", "") ?: "",
        )
    }

    /**
     * One-time migration: moves API keys from plain SharedPreferences
     * to EncryptedSharedPreferences and deletes the plain-text copies.
     * Synchronized to prevent duplicate migrations from concurrent load() calls.
     */
    @Synchronized
    private fun migrateApiKeysToSecurePrefs() {
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) return

        val sp = securePrefs ?: return  // Can't migrate if crypto unavailable
        try {
            val editor = sp.edit()
            val plainEditor = prefs.edit()
            var migrated = false

            for (key in API_KEY_NAMES) {
                val value = prefs.getString(key, null)
                if (!value.isNullOrEmpty()) {
                    editor.putString(key, value)
                    plainEditor.remove(key)
                    migrated = true
                    Log.i(TAG, "Migrated $key to secure storage")
                }
            }

            if (migrated) {
                // Use commit() (synchronous) for security-critical migration:
                // apply() is async and could leave keys in both stores if process is killed.
                editor.commit()
                plainEditor.commit()
            }
            // Mark migration as done even if no keys existed (don't re-check every time)
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).commit()
        } catch (e: Exception) {
            Log.w(TAG, "API key migration failed — keys remain in plain prefs", e)
        }
    }

    private fun save(p: TerminalPreferences) {
        // Non-sensitive settings → regular SharedPreferences
        prefs.edit()
            .putInt("font_size", p.fontSize)
            .putString("font_family", p.fontFamily)
            .putBoolean("keep_screen_on", p.keepScreenOn)
            .putBoolean("haptic_feedback", p.hapticFeedback)
            .putBoolean("bell_enabled", p.bellEnabled)
            .putBoolean("show_extra_keys", p.showExtraKeys)
            .putString("extra_keys_style", p.extraKeysStyle)
            .putBoolean("back_is_escape", p.backIsEscape)
            .putString("color_scheme", p.colorScheme)
            .putBoolean("use_rust_backend", p.useRustBackend)
            .putBoolean("use_gpu_renderer", p.useGpuRenderer)
            .putBoolean("pip_on_leave", p.pipOnLeave)
            .putBoolean("session_grouping_enabled", p.sessionGroupingEnabled)
            .putBoolean("mcp_enabled", p.mcpEnabled)
            .putInt("mcp_port", p.mcpPort)
            .putBoolean("llm_enabled", p.llmEnabled)
            .putInt("scrollback_lines", p.scrollbackLines)
            .apply()

        // API keys → EncryptedSharedPreferences (AES-256-GCM, skipped if unavailable)
        securePrefs?.edit()
            ?.putString("api_key_anthropic", p.anthropicApiKey)
            ?.putString("api_key_google", p.googleApiKey)
            ?.putString("api_key_openai", p.openaiApiKey)
            ?.putString("api_key_openrouter", p.openrouterApiKey)
            ?.apply()
    }
}
