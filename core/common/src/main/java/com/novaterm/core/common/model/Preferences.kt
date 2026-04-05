package com.novaterm.core.common.model

data class TerminalConfig(
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val fontFamily: String = "monospace",
    val colorScheme: ColorScheme = ColorScheme.GRUVBOX_DARK,
    val scrollbackLines: Int = 10_000,
    val keepScreenOn: Boolean = false,
    val hapticFeedback: Boolean = true,
    val bellEnabled: Boolean = true,
    val showExtraKeys: Boolean = true,
    val backIsEscape: Boolean = true,
    val terminalType: String = "xterm-256color",
    val useRustBackend: Boolean = false,
) {
    init {
        require(fontSize in FONT_SIZE_RANGE) { "fontSize must be in $FONT_SIZE_RANGE, got $fontSize" }
        require(scrollbackLines in SCROLLBACK_RANGE) { "scrollbackLines must be in $SCROLLBACK_RANGE, got $scrollbackLines" }
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
enum class ColorScheme(val displayName: String, val id: String) {
    GRUVBOX_DARK("Gruvbox Dark", "gruvbox-dark"),
    GRUVBOX_LIGHT("Gruvbox Light", "gruvbox-light"),
    CATPPUCCIN_MOCHA("Catppuccin Mocha", "catppuccin-mocha"),
    MONOKAI("Monokai", "monokai"),
    DRACULA("Dracula", "dracula"),
    SOLARIZED_DARK("Solarized Dark", "solarized-dark"),
    NORD("Nord", "nord"),
    SYSTEM("System Dynamic", "system"),
    ;

    companion object {
        /** Lookup by [id], returns [GRUVBOX_DARK] for unknown IDs. */
        fun fromId(id: String): ColorScheme =
            entries.find { it.id == id } ?: GRUVBOX_DARK
    }
}
