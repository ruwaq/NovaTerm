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

    data class SchemeInfo(
        val scheme: ColorScheme,
        val isDark: Boolean,
    ) {
        /** Stable string key — delegates to [ColorScheme.id]. */
        val id: String get() = scheme.id
        /** Human-readable label — delegates to [ColorScheme.displayName]. */
        val displayName: String get() = scheme.displayName
    }

    val ALL: List<SchemeInfo> = listOf(
        SchemeInfo(ColorScheme.GRUVBOX_DARK, isDark = true),
        SchemeInfo(ColorScheme.GRUVBOX_LIGHT, isDark = false),
        SchemeInfo(ColorScheme.CATPPUCCIN_MOCHA, isDark = true),
        SchemeInfo(ColorScheme.SOLARIZED_DARK, isDark = true),
        SchemeInfo(ColorScheme.MONOKAI, isDark = true),
        SchemeInfo(ColorScheme.NORD, isDark = true),
        SchemeInfo(ColorScheme.DRACULA, isDark = true),
    )

    val DEFAULT_ID: String = ColorScheme.GRUVBOX_DARK.id

    fun isDark(schemeId: String): Boolean =
        ALL.find { it.id == schemeId }?.isDark ?: true

    fun displayName(schemeId: String): String =
        ALL.find { it.id == schemeId }?.displayName ?: schemeId
}
