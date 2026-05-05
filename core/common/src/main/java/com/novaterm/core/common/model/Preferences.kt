package com.novaterm.core.common.model

import com.novaterm.core.common.model.ConfigConstants

data class TerminalConfig(
    val fontSize: Int = ConfigConstants.DEFAULT_FONT_SIZE,
    val fontFamily: String = "monospace",
    val colorScheme: ColorScheme = ColorScheme.GRUVBOX_DARK,
    val scrollbackLines: Int = ConfigConstants.DEFAULT_SCROLLBACK_LINES,
    val keepScreenOn: Boolean = false,
    val hapticFeedback: Boolean = true,
    val bellEnabled: Boolean = true,
    val showExtraKeys: Boolean = true,
    val backIsEscape: Boolean = true,
    val terminalType: String = "xterm-256color",
    val useRustBackend: Boolean = true,
) {
    init {
        require(fontSize in ConfigConstants.FONT_SIZE_RANGE) { "fontSize must be in ${ConfigConstants.FONT_SIZE_RANGE}, got $fontSize" }
        require(scrollbackLines in ConfigConstants.SCROLLBACK_RANGE) { "scrollbackLines must be in ${ConfigConstants.SCROLLBACK_RANGE}, got $scrollbackLines" }
        require(fontFamily.isNotBlank()) { "fontFamily must not be blank" }
        require(terminalType.isNotBlank()) { "terminalType must not be blank" }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 12
        val FONT_SIZE_RANGE = 8..32
        val SCROLLBACK_RANGE = 0..100_000
    }
}

/**
 * Single source of truth for color scheme identity.
 *
 * [id] is the stable string key used in DataStore, TerminalPalettes, and
 * ColorSchemes. [displayName] is the human-readable label. New schemes must
 * be added here first, then implemented in TerminalPalettes.
 */
enum class ColorScheme(val displayName: String, val id: String, val isDark: Boolean) {
    GRUVBOX_DARK("Gruvbox Dark", "gruvbox-dark", true),
    GRUVBOX_LIGHT("Gruvbox Light", "gruvbox-light", false),
    CATPPUCCIN_MOCHA("Catppuccin Mocha", "catppuccin-mocha", true),
    MONOKAI("Monokai", "monokai", true),
    DRACULA("Dracula", "dracula", true),
    SOLARIZED_DARK("Solarized Dark", "solarized-dark", true),
    NORD("Nord", "nord", true),
    SYSTEM("System Dynamic", "system", false);  // SYSTEM uses dynamic colors, not a terminal palette

    companion object {
        /** Lookup by [id], returns [GRUVBOX_DARK] for unknown IDs. */
        fun fromId(id: String): ColorScheme =
            entries.find { it.id == id } ?: GRUVBOX_DARK
    }
}
