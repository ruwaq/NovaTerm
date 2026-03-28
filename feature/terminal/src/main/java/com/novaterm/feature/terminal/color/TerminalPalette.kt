package com.novaterm.feature.terminal.color

/**
 * Terminal ANSI color palette: 16 colors + foreground/background/cursor.
 * Each scheme defines warm, eye-comfort colors optimized for AMOLED.
 *
 * Colors are stored as ARGB Int (0xAARRGGBB) to match Termux's
 * TerminalColors.COLOR_SCHEME.mDefaultColors format.
 */
data class TerminalPalette(
    val ansi: IntArray,       // 16 ANSI colors (indices 0-15)
    val foreground: Int,
    val background: Int,
    val cursor: Int,
) {
    init {
        require(ansi.size == 16) { "ANSI palette must have exactly 16 colors" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalPalette) return false
        return ansi.contentEquals(other.ansi) &&
            foreground == other.foreground &&
            background == other.background &&
            cursor == other.cursor
    }

    override fun hashCode(): Int {
        var result = ansi.contentHashCode()
        result = 31 * result + foreground
        result = 31 * result + background
        result = 31 * result + cursor
        return result
    }
}

/**
 * All available terminal color palettes, keyed by scheme ID string
 * matching [com.novaterm.feature.settings.data.TerminalPreferences.colorScheme].
 */
object TerminalPalettes {

    fun forScheme(schemeId: String): TerminalPalette = when (schemeId) {
        "gruvbox-dark" -> GRUVBOX_DARK
        "gruvbox-light" -> GRUVBOX_LIGHT
        "solarized-dark" -> SOLARIZED_DARK
        "monokai" -> MONOKAI
        "nord" -> NORD
        "dracula" -> DRACULA
        "catppuccin-mocha" -> CATPPUCCIN_MOCHA
        else -> GRUVBOX_DARK
    }

    // ── Gruvbox Dark (Soft) ──────────────────────────────────
    // Warm, no harsh blues. Default NovaTerm palette.
    val GRUVBOX_DARK = TerminalPalette(
        ansi = intArrayOf(
            0xFF3C3836.toInt(), // 0  black
            0xFFEA6962.toInt(), // 1  red
            0xFFA9B665.toInt(), // 2  green
            0xFFD8A657.toInt(), // 3  yellow
            0xFF7DAEA3.toInt(), // 4  blue (warm cyan)
            0xFFD3869B.toInt(), // 5  magenta
            0xFF89B482.toInt(), // 6  cyan
            0xFFA89984.toInt(), // 7  white
            0xFF928374.toInt(), // 8  bright black
            0xFFEA6962.toInt(), // 9  bright red
            0xFFB8BB26.toInt(), // 10 bright green
            0xFFFABD2F.toInt(), // 11 bright yellow
            0xFF83A598.toInt(), // 12 bright blue
            0xFFD3869B.toInt(), // 13 bright magenta
            0xFF8EC07C.toInt(), // 14 bright cyan
            0xFFEBDBB2.toInt(), // 15 bright white
        ),
        foreground = 0xFFEBDBB2.toInt(),
        background = 0xFF3C3836.toInt(),
        cursor = 0xFFE78A4E.toInt(), // Ember Orange
    )

    // ── Gruvbox Light ────────────────────────────────────────
    val GRUVBOX_LIGHT = TerminalPalette(
        ansi = intArrayOf(
            0xFFFBF1C7.toInt(), // 0  black (light bg)
            0xFFCC241D.toInt(), // 1  red
            0xFF98971A.toInt(), // 2  green
            0xFFD79921.toInt(), // 3  yellow
            0xFF458588.toInt(), // 4  blue
            0xFFB16286.toInt(), // 5  magenta
            0xFF689D6A.toInt(), // 6  cyan
            0xFF7C6F64.toInt(), // 7  white (dark fg)
            0xFF928374.toInt(), // 8  bright black
            0xFF9D0006.toInt(), // 9  bright red
            0xFF79740E.toInt(), // 10 bright green
            0xFFB57614.toInt(), // 11 bright yellow
            0xFF076678.toInt(), // 12 bright blue
            0xFF8F3F71.toInt(), // 13 bright magenta
            0xFF427B58.toInt(), // 14 bright cyan
            0xFF3C3836.toInt(), // 15 bright white (dark)
        ),
        foreground = 0xFF3C3836.toInt(),
        background = 0xFFFBF1C7.toInt(),
        cursor = 0xFFAF5D0E.toInt(),
    )

    // ── Solarized Dark ───────────────────────────────────────
    val SOLARIZED_DARK = TerminalPalette(
        ansi = intArrayOf(
            0xFF073642.toInt(), // 0  black (base02)
            0xFFDC322F.toInt(), // 1  red
            0xFF859900.toInt(), // 2  green
            0xFFB58900.toInt(), // 3  yellow
            0xFF268BD2.toInt(), // 4  blue
            0xFFD33682.toInt(), // 5  magenta
            0xFF2AA198.toInt(), // 6  cyan
            0xFFEEE8D5.toInt(), // 7  white (base2)
            0xFF002B36.toInt(), // 8  bright black (base03)
            0xFFCB4B16.toInt(), // 9  bright red (orange)
            0xFF586E75.toInt(), // 10 bright green (base01)
            0xFF657B83.toInt(), // 11 bright yellow (base00)
            0xFF839496.toInt(), // 12 bright blue (base0)
            0xFF6C71C4.toInt(), // 13 bright magenta (violet)
            0xFF93A1A1.toInt(), // 14 bright cyan (base1)
            0xFFFDF6E3.toInt(), // 15 bright white (base3)
        ),
        foreground = 0xFF839496.toInt(),
        background = 0xFF002B36.toInt(),
        cursor = 0xFF93A1A1.toInt(),
    )

    // ── Monokai ──────────────────────────────────────────────
    val MONOKAI = TerminalPalette(
        ansi = intArrayOf(
            0xFF272822.toInt(), // 0  black
            0xFFF92672.toInt(), // 1  red (pink)
            0xFFA6E22E.toInt(), // 2  green
            0xFFF4BF75.toInt(), // 3  yellow
            0xFF66D9EF.toInt(), // 4  blue (cyan)
            0xFFAE81FF.toInt(), // 5  magenta (purple)
            0xFFA1EFE4.toInt(), // 6  cyan
            0xFFF8F8F2.toInt(), // 7  white
            0xFF75715E.toInt(), // 8  bright black (comment)
            0xFFF92672.toInt(), // 9  bright red
            0xFFA6E22E.toInt(), // 10 bright green
            0xFFE6DB74.toInt(), // 11 bright yellow
            0xFF66D9EF.toInt(), // 12 bright blue
            0xFFAE81FF.toInt(), // 13 bright magenta
            0xFFA1EFE4.toInt(), // 14 bright cyan
            0xFFF9F8F5.toInt(), // 15 bright white
        ),
        foreground = 0xFFF8F8F2.toInt(),
        background = 0xFF272822.toInt(),
        cursor = 0xFFF8F8F0.toInt(),
    )

    // ── Nord ─────────────────────────────────────────────────
    val NORD = TerminalPalette(
        ansi = intArrayOf(
            0xFF3B4252.toInt(), // 0  black (nord1)
            0xFFBF616A.toInt(), // 1  red
            0xFFA3BE8C.toInt(), // 2  green
            0xFFEBCB8B.toInt(), // 3  yellow
            0xFF81A1C1.toInt(), // 4  blue
            0xFFB48EAD.toInt(), // 5  magenta
            0xFF88C0D0.toInt(), // 6  cyan
            0xFFE5E9F0.toInt(), // 7  white (nord5)
            0xFF4C566A.toInt(), // 8  bright black (nord3)
            0xFFBF616A.toInt(), // 9  bright red
            0xFFA3BE8C.toInt(), // 10 bright green
            0xFFEBCB8B.toInt(), // 11 bright yellow
            0xFF81A1C1.toInt(), // 12 bright blue
            0xFFB48EAD.toInt(), // 13 bright magenta
            0xFF8FBCBB.toInt(), // 14 bright cyan (nord7)
            0xFFECEFF4.toInt(), // 15 bright white (nord6)
        ),
        foreground = 0xFFD8DEE9.toInt(),
        background = 0xFF2E3440.toInt(),
        cursor = 0xFFD8DEE9.toInt(),
    )

    // ── Dracula ──────────────────────────────────────────────
    val DRACULA = TerminalPalette(
        ansi = intArrayOf(
            0xFF21222C.toInt(), // 0  black
            0xFFFF5555.toInt(), // 1  red
            0xFF50FA7B.toInt(), // 2  green
            0xFFF1FA8C.toInt(), // 3  yellow
            0xFFBD93F9.toInt(), // 4  blue (purple)
            0xFFFF79C6.toInt(), // 5  magenta (pink)
            0xFF8BE9FD.toInt(), // 6  cyan
            0xFFF8F8F2.toInt(), // 7  white
            0xFF6272A4.toInt(), // 8  bright black (comment)
            0xFFFF6E6E.toInt(), // 9  bright red
            0xFF69FF94.toInt(), // 10 bright green
            0xFFFFFFA5.toInt(), // 11 bright yellow
            0xFFD6ACFF.toInt(), // 12 bright blue
            0xFFFF92DF.toInt(), // 13 bright magenta
            0xFFA4FFFF.toInt(), // 14 bright cyan
            0xFFFFFFFF.toInt(), // 15 bright white
        ),
        foreground = 0xFFF8F8F2.toInt(),
        background = 0xFF282A36.toInt(),
        cursor = 0xFFF8F8F2.toInt(),
    )

    // ── Catppuccin Mocha ─────────────────────────────────────
    // #1 most popular scheme. Pastel colors, dark warm background.
    val CATPPUCCIN_MOCHA = TerminalPalette(
        ansi = intArrayOf(
            0xFF45475A.toInt(), // 0  black (surface1)
            0xFFF38BA8.toInt(), // 1  red
            0xFFA6E3A1.toInt(), // 2  green
            0xFFF9E2AF.toInt(), // 3  yellow
            0xFF89B4FA.toInt(), // 4  blue
            0xFFF5C2E7.toInt(), // 5  magenta (pink)
            0xFF94E2D5.toInt(), // 6  cyan (teal)
            0xFFBAC2DE.toInt(), // 7  white (subtext1)
            0xFF585B70.toInt(), // 8  bright black (surface2)
            0xFFF38BA8.toInt(), // 9  bright red
            0xFFA6E3A1.toInt(), // 10 bright green
            0xFFF9E2AF.toInt(), // 11 bright yellow
            0xFF89B4FA.toInt(), // 12 bright blue
            0xFFF5C2E7.toInt(), // 13 bright magenta
            0xFF94E2D5.toInt(), // 14 bright cyan
            0xFFA6ADC8.toInt(), // 15 bright white (subtext0)
        ),
        foreground = 0xFFCDD6F4.toInt(), // text
        background = 0xFF1E1E2E.toInt(), // base
        cursor = 0xFFF5E0DC.toInt(),     // rosewater
    )
}
