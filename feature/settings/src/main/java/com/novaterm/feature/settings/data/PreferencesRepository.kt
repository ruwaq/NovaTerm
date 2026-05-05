package com.novaterm.feature.settings.data

import android.content.Context
import android.content.SharedPreferences
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
    val useRustBackend: Boolean = true,
    val useGpuRenderer: Boolean = true,
    val pipOnLeave: Boolean = false,
    val sessionGroupingEnabled: Boolean = true,
    val scrollbackLines: Int = DEFAULT_SCROLLBACK_LINES,
) {
    companion object {
        const val EXTRA_KEYS_STYLE_DEFAULT = "default"
        const val EXTRA_KEYS_STYLE_VIM = "vim"
        const val EXTRA_KEYS_STYLE_DEV = "dev"
        const val EXTRA_KEYS_STYLE_MINIMAL = "minimal"
        val EXTRA_KEYS_STYLES = listOf(
            EXTRA_KEYS_STYLE_DEFAULT,
            EXTRA_KEYS_STYLE_VIM,
            EXTRA_KEYS_STYLE_DEV,
            EXTRA_KEYS_STYLE_MINIMAL,
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

    companion object {
        const val PREFS_NAME = "novaterm_prefs"
    }

    private val _preferences = MutableStateFlow(load())
    val preferences: StateFlow<TerminalPreferences> = _preferences.asStateFlow()

    val isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
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
            useGpuRenderer = prefs.getBoolean("use_gpu_renderer", true),
            pipOnLeave = prefs.getBoolean("pip_on_leave", false),
            sessionGroupingEnabled = prefs.getBoolean("session_grouping_enabled", true),
            scrollbackLines = prefs.getInt("scrollback_lines", TerminalPreferences.DEFAULT_SCROLLBACK_LINES),
        )
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
            .putInt("scrollback_lines", p.scrollbackLines)
            .apply()
    }
}
