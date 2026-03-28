package com.novaterm.core.common.model

/**
 * Single source of truth for all available color schemes.
 *
 * Used by:
 * - feature/settings/SettingsScreen (dropdown)
 * - feature/settings/ColorSchemePickerScreen (onboarding)
 * - feature/terminal/TerminalPalettes (ANSI color resolution)
 *
 * Adding a new scheme: add it here, then implement the palette in TerminalPalettes
 * and the preview in ColorSchemePickerScreen.
 */
object ColorSchemes {

    data class SchemeInfo(
        val id: String,
        val displayName: String,
        val isDark: Boolean,
    )

    val ALL: List<SchemeInfo> = listOf(
        SchemeInfo("gruvbox-dark", "Gruvbox Dark", isDark = true),
        SchemeInfo("gruvbox-light", "Gruvbox Light", isDark = false),
        SchemeInfo("catppuccin-mocha", "Catppuccin Mocha", isDark = true),
        SchemeInfo("solarized-dark", "Solarized Dark", isDark = true),
        SchemeInfo("monokai", "Monokai", isDark = true),
        SchemeInfo("nord", "Nord", isDark = true),
        SchemeInfo("dracula", "Dracula", isDark = true),
    )

    val DEFAULT_ID = "gruvbox-dark"

    fun isDark(schemeId: String): Boolean =
        ALL.find { it.id == schemeId }?.isDark ?: true

    fun displayName(schemeId: String): String =
        ALL.find { it.id == schemeId }?.displayName ?: schemeId
}
