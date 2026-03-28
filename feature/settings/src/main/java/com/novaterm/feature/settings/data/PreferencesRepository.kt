package com.novaterm.feature.settings.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TerminalPreferences(
    val fontSize: Int = 18,  // Readable on 6.83" AMOLED
    val keepScreenOn: Boolean = false,
    val hapticFeedback: Boolean = true,
    val bellEnabled: Boolean = true,
    val showExtraKeys: Boolean = true,
    val backIsEscape: Boolean = true,
    val colorScheme: String = "gruvbox-dark",
)

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("novaterm_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(load())
    val preferences: StateFlow<TerminalPreferences> = _preferences.asStateFlow()

    val isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }

    fun update(newPrefs: TerminalPreferences) {
        save(newPrefs)
        _preferences.value = newPrefs
    }

    private fun load(): TerminalPreferences {
        return TerminalPreferences(
            fontSize = prefs.getInt("font_size", 18),
            keepScreenOn = prefs.getBoolean("keep_screen_on", false),
            hapticFeedback = prefs.getBoolean("haptic_feedback", true),
            bellEnabled = prefs.getBoolean("bell_enabled", true),
            showExtraKeys = prefs.getBoolean("show_extra_keys", true),
            backIsEscape = prefs.getBoolean("back_is_escape", true),
            colorScheme = prefs.getString("color_scheme", "gruvbox-dark") ?: "gruvbox-dark",
        )
    }

    private fun save(p: TerminalPreferences) {
        prefs.edit()
            .putInt("font_size", p.fontSize)
            .putBoolean("keep_screen_on", p.keepScreenOn)
            .putBoolean("haptic_feedback", p.hapticFeedback)
            .putBoolean("bell_enabled", p.bellEnabled)
            .putBoolean("show_extra_keys", p.showExtraKeys)
            .putBoolean("back_is_escape", p.backIsEscape)
            .putString("color_scheme", p.colorScheme)
            .apply()
    }
}
