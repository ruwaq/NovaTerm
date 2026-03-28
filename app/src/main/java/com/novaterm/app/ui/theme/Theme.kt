package com.novaterm.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Gruvbox canonical palette ──────────────────────────────

object Gruvbox {
    // Backgrounds (dark)
    val Bg0Hard = Color(0xFF1D2021)
    val Bg0 = Color(0xFF282828)
    val Bg1 = Color(0xFF3C3836)
    val Bg2 = Color(0xFF504945)
    val Bg3 = Color(0xFF665C54)
    val Bg4 = Color(0xFF7C6F64)

    // Foregrounds (dark)
    val Fg0 = Color(0xFFFBF1C7)
    val Fg1 = Color(0xFFEBDBB2)
    val Fg2 = Color(0xFFD5C4A1)
    val Fg3 = Color(0xFFBDAE93)
    val Fg4 = Color(0xFFA89984)

    // Accent colors (dark/neutral)
    val Red = Color(0xFFCC241D)
    val RedBright = Color(0xFFFB4934)
    val Green = Color(0xFF98971A)
    val GreenBright = Color(0xFFB8BB26)
    val Yellow = Color(0xFFD79921)
    val YellowBright = Color(0xFFFABD2F)
    val Blue = Color(0xFF458588)
    val BlueBright = Color(0xFF83A598)
    val Purple = Color(0xFFB16286)
    val PurpleBright = Color(0xFFD3869B)
    val Aqua = Color(0xFF689D6A)
    val AquaBright = Color(0xFF8EC07C)
    val Orange = Color(0xFFD65D0E)
    val OrangeBright = Color(0xFFFE8019)

    // Backgrounds (light)
    val LightBg0Hard = Color(0xFFF9F5D7)
    val LightBg0 = Color(0xFFFBF1C7)
    val LightBg1 = Color(0xFFEBDBB2)
    val LightBg2 = Color(0xFFD5C4A1)
    val LightFg0 = Color(0xFF282828)
    val LightFg1 = Color(0xFF3C3836)
    val LightFg2 = Color(0xFF504945)
    val LightFg4 = Color(0xFF7C6F64)
}

/**
 * Extended color palette for terminal-specific needs beyond Material3.
 * Provides access to the full Gruvbox palette for custom components.
 */
@Immutable
data class NovaTermColors(
    val terminalBackground: Color = Gruvbox.Bg0Hard,
    val terminalForeground: Color = Gruvbox.Fg1,
    val tabBarBackground: Color = Gruvbox.Bg0,
    val destructive: Color = Gruvbox.RedBright,
    val accent: Color = Gruvbox.OrangeBright,
)

val LocalNovaTermColors = staticCompositionLocalOf { NovaTermColors() }

// ── Material3 color schemes ────────────────────────────────

private val GruvboxDark = darkColorScheme(
    primary = Gruvbox.OrangeBright,
    onPrimary = Gruvbox.Bg0Hard,
    primaryContainer = Gruvbox.Bg1,
    onPrimaryContainer = Gruvbox.Fg1,
    secondary = Gruvbox.AquaBright,
    onSecondary = Gruvbox.Bg0Hard,
    secondaryContainer = Gruvbox.Bg2,
    onSecondaryContainer = Gruvbox.Fg1,
    tertiary = Gruvbox.BlueBright,
    onTertiary = Gruvbox.Bg0Hard,
    tertiaryContainer = Gruvbox.Bg2,
    onTertiaryContainer = Gruvbox.Fg1,
    background = Gruvbox.Bg0Hard,
    onBackground = Gruvbox.Fg1,
    surface = Gruvbox.Bg0,
    onSurface = Gruvbox.Fg1,
    surfaceVariant = Gruvbox.Bg1,
    onSurfaceVariant = Gruvbox.Fg4,
    surfaceContainerHighest = Gruvbox.Bg2,
    outline = Gruvbox.Bg4,
    outlineVariant = Gruvbox.Bg3,
    error = Gruvbox.RedBright,
    onError = Gruvbox.Bg0Hard,
    errorContainer = Gruvbox.Red,
    onErrorContainer = Gruvbox.Fg1,
    inverseSurface = Gruvbox.Fg1,
    inverseOnSurface = Gruvbox.Bg0,
    inversePrimary = Gruvbox.Orange,
    scrim = Color.Black,
)

private val GruvboxLight = lightColorScheme(
    primary = Gruvbox.Orange,
    onPrimary = Gruvbox.LightBg0,
    primaryContainer = Gruvbox.LightBg1,
    onPrimaryContainer = Gruvbox.LightFg1,
    secondary = Gruvbox.Aqua,
    onSecondary = Gruvbox.LightBg0,
    secondaryContainer = Gruvbox.LightBg2,
    onSecondaryContainer = Gruvbox.LightFg1,
    tertiary = Gruvbox.Blue,
    onTertiary = Gruvbox.LightBg0,
    tertiaryContainer = Gruvbox.LightBg2,
    onTertiaryContainer = Gruvbox.LightFg1,
    background = Gruvbox.LightBg0,
    onBackground = Gruvbox.LightFg1,
    surface = Gruvbox.LightBg0Hard,
    onSurface = Gruvbox.LightFg1,
    surfaceVariant = Gruvbox.LightBg1,
    onSurfaceVariant = Gruvbox.LightFg4,
    outline = Gruvbox.LightFg4,
    outlineVariant = Gruvbox.LightBg2,
    error = Gruvbox.Red,
    onError = Gruvbox.LightBg0,
    errorContainer = Color(0xFFFCDCD7),
    onErrorContainer = Gruvbox.Red,
    inverseSurface = Gruvbox.LightFg1,
    inverseOnSurface = Gruvbox.LightBg0,
    inversePrimary = Gruvbox.OrangeBright,
    scrim = Color.Black,
)

// ── Theme composable ───────────────────────────────────────

@Composable
fun NovaTermTheme(
    darkTheme: Boolean = true, // Terminal apps default to dark
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GruvboxDark
        else -> GruvboxLight
    }

    val novaTermColors = if (darkTheme) {
        NovaTermColors()
    } else {
        NovaTermColors(
            terminalBackground = Gruvbox.LightBg0Hard,
            terminalForeground = Gruvbox.LightFg1,
            tabBarBackground = Gruvbox.LightBg0,
            destructive = Gruvbox.Red,
            accent = Gruvbox.Orange,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalNovaTermColors provides novaTermColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
