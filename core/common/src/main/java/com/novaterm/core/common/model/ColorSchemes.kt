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
/**
 * Palette metadata for available color schemes.
 *
 * [ColorScheme] (in Preferences.kt) is the single source of truth for IDs
 * and display names. This object adds the [isDark] flag that controls whether
 * the Compose theme should switch to EmberLight.
 *
 * SYSTEM is intentionally excluded from ALL — it uses Material You dynamic
 * colors and has no terminal palette.
 */
object ColorSchemes {
    val ALL: List<ColorScheme> = listOf(
        ColorScheme.GRUVBOX_DARK,
        ColorScheme.GRUVBOX_LIGHT,
        ColorScheme.CATPPUCCIN_MOCHA,
        ColorScheme.SOLARIZED_DARK,
        ColorScheme.MONOKAI,
        ColorScheme.NORD,
        ColorScheme.DRACULA
    )

    val DEFAULT_ID: String = ColorScheme.GRUVBOX_DARK.id

    fun isDark(schemeId: String): Boolean =
        ColorScheme.fromId(schemeId).isDark

    fun displayName(schemeId: String): String =
        ALL.find { it.id == schemeId }?.displayName ?: schemeId
}
