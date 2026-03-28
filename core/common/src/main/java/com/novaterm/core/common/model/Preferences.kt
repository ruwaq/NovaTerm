package com.novaterm.core.common.model

data class TerminalConfig(
    val fontSize: Int = 14,
    val fontFamily: String = "monospace",
    val colorScheme: ColorScheme = ColorScheme.GRUVBOX_DARK,
    val scrollbackLines: Int = 10_000,
    val keepScreenOn: Boolean = false,
    val hapticFeedback: Boolean = true,
    val bellEnabled: Boolean = true,
    val showExtraKeys: Boolean = true,
    val backIsEscape: Boolean = false,
    val terminalType: String = "xterm-256color",
) {
    init {
        require(fontSize in FONT_SIZE_RANGE) { "fontSize must be in $FONT_SIZE_RANGE, got $fontSize" }
        require(scrollbackLines >= 0) { "scrollbackLines must be >= 0, got $scrollbackLines" }
    }

    companion object {
        val FONT_SIZE_RANGE = 8..32
    }
}

enum class ColorScheme(val displayName: String) {
    GRUVBOX_DARK("Gruvbox Dark"),
    GRUVBOX_LIGHT("Gruvbox Light"),
    MONOKAI("Monokai"),
    DRACULA("Dracula"),
    SOLARIZED_DARK("Solarized Dark"),
    NORD("Nord"),
    SYSTEM("System Dynamic"),
}
