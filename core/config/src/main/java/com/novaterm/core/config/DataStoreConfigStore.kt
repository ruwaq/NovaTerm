package com.novaterm.core.config

import android.content.Context
import com.novaterm.core.common.model.ConfigConstants
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novaterm.core.common.contract.ConfigStore
import com.novaterm.core.common.model.ColorScheme
import com.novaterm.core.common.model.TerminalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "novaterm_config")

class DataStoreConfigStore(
    private val context: Context,
    private val scope: CoroutineScope,
) : ConfigStore {

    private val _config = MutableStateFlow(TerminalConfig())
    override val config: StateFlow<TerminalConfig> = _config.asStateFlow()

    init {
        scope.launch {
            context.dataStore.data.collect { prefs ->
                _config.value = prefs.toTerminalConfig()
            }
        }
    }

    /**
     * Atomically update config through DataStore.
     *
     * Previous version had a race condition: it read _config.value, applied the
     * transform, wrote to DataStore in a fire-and-forget coroutine, AND eagerly
     * set _config.value. Two rapid calls could overwrite each other.
     *
     * Now we use DataStore.edit (which is itself atomic) and let the collect
     * in init{} propagate the new value to _config. This is safe because
     * DataStore serializes edit calls internally.
     */
    override suspend fun update(transform: (TerminalConfig) -> TerminalConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs.toTerminalConfig()
            val newConfig = transform(current)
            prefs[Keys.FONT_SIZE] = newConfig.fontSize
            prefs[Keys.FONT_FAMILY] = newConfig.fontFamily
            prefs[Keys.COLOR_SCHEME] = newConfig.colorScheme.name
            prefs[Keys.SCROLLBACK_LINES] = newConfig.scrollbackLines
            prefs[Keys.KEEP_SCREEN_ON] = newConfig.keepScreenOn
            prefs[Keys.HAPTIC_FEEDBACK] = newConfig.hapticFeedback
            prefs[Keys.BELL_ENABLED] = newConfig.bellEnabled
            prefs[Keys.SHOW_EXTRA_KEYS] = newConfig.showExtraKeys
            prefs[Keys.BACK_IS_ESCAPE] = newConfig.backIsEscape
            prefs[Keys.TERMINAL_TYPE] = newConfig.terminalType
            prefs[Keys.USE_RUST_BACKEND] = newConfig.useRustBackend
        }
        // The collect{} in init propagates new values to _config automatically.
        // No need for a second read — DataStore.edit already suspends until persisted.
    }

    private fun Preferences.toTerminalConfig(): TerminalConfig {
        val fontSize = (this[Keys.FONT_SIZE] ?: ConfigConstants.DEFAULT_FONT_SIZE)
            .coerceIn(ConfigConstants.FONT_SIZE_RANGE)
        val scrollback = (this[Keys.SCROLLBACK_LINES] ?: ConfigConstants.DEFAULT_SCROLLBACK_LINES)
            .coerceIn(ConfigConstants.SCROLLBACK_RANGE)
        val schemeName = this[Keys.COLOR_SCHEME] ?: ColorScheme.GRUVBOX_DARK.name
        val scheme = try {
            ColorScheme.valueOf(schemeName)
        } catch (_: IllegalArgumentException) {
            ColorScheme.GRUVBOX_DARK
        }

        return TerminalConfig(
            fontSize = fontSize,
            fontFamily = this[Keys.FONT_FAMILY] ?: "monospace",
            colorScheme = scheme,
            scrollbackLines = scrollback,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: false,
            hapticFeedback = this[Keys.HAPTIC_FEEDBACK] ?: true,
            bellEnabled = this[Keys.BELL_ENABLED] ?: true,
            showExtraKeys = this[Keys.SHOW_EXTRA_KEYS] ?: true,
            backIsEscape = this[Keys.BACK_IS_ESCAPE] ?: true,
            terminalType = this[Keys.TERMINAL_TYPE] ?: "xterm-256color",
            useRustBackend = this[Keys.USE_RUST_BACKEND] ?: false,
        )
    }

    private object Keys {
        val FONT_SIZE = intPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val SCROLLBACK_LINES = intPreferencesKey("scrollback_lines")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val BELL_ENABLED = booleanPreferencesKey("bell_enabled")
        val SHOW_EXTRA_KEYS = booleanPreferencesKey("show_extra_keys")
        val BACK_IS_ESCAPE = booleanPreferencesKey("back_is_escape")
        val TERMINAL_TYPE = stringPreferencesKey("terminal_type")
        val USE_RUST_BACKEND = booleanPreferencesKey("use_rust_backend")
    }
}
