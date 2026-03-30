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
        val FONT_SIZE_RANGE = 6..48
        val SCROLLBACK_RANGE = 0..100_000
    }
}

enum class ColorScheme(val displayName: String) {
    GRUVBOX_DARK("Gruvbox Dark"),
    GRUVBOX_LIGHT("Gruvbox Light"),
    CATPPUCCIN_MOCHA("Catppuccin Mocha"),
    MONOKAI("Monokai"),
    DRACULA("Dracula"),
    SOLARIZED_DARK("Solarized Dark"),
    NORD("Nord"),
    SYSTEM("System Dynamic"),
}
