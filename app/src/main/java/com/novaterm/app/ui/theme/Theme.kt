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

// ── NovaTerm Ember palette ──────────────────────────────
// Based on Gruvbox Material, with ALL blues replaced by warm
// sage/green-gray tones. Optimized for AMOLED + eye comfort.
// Zero blue tint. Background is warm dark gray, not pure black.
// All foreground colors pass WCAG AA (4.5:1+) against background.

object Ember {
    // Backgrounds (warm dark — R > G > B, no blue tint)
    val Bg0Hard = Color(0xFF1A1816)  // AMOLED-friendly, no halation
    val Bg0 = Color(0xFF22201E)      // Surface
    val Bg1 = Color(0xFF3C3836)      // Surface variant
    val Bg2 = Color(0xFF504945)      // Elevated surface
    val Bg3 = Color(0xFF665C54)      // Borders
    val Bg4 = Color(0xFF7C6F64)      // Muted elements

    // Foregrounds (warm cream — easy on eyes)
    val Fg0 = Color(0xFFDDC7A1)      // Bright text (10.76:1)
    val Fg1 = Color(0xFFD4BE98)      // Normal text (9.79:1)
    val Fg2 = Color(0xFFC5B18A)      // Secondary text
    val Fg3 = Color(0xFFBDAE93)      // Tertiary text
    val Fg4 = Color(0xFFA89984)      // Muted text (6.37:1)
    val Comment = Color(0xFF928374)   // Comments (4.82:1 AA)

    // Accent colors — ALL warm, ZERO blue
    val Red = Color(0xFFEA6962)       // Errors, deletes (5.64:1)
    val Orange = Color(0xFFE78A4E)    // Primary accent (6.87:1)
    val Yellow = Color(0xFFD8A657)    // Strings, warnings (8.02:1)
    val Green = Color(0xFFA9B665)     // Success, adds (8.06:1)
    val Sage = Color(0xFF8B9E82)      // Replaces blue! Warm green-gray (6.16:1)
    val Aqua = Color(0xFF89B482)      // Types, paths (7.52:1)
    val Purple = Color(0xFFD3869B)    // Keywords (6.45:1)

    // Bright variants
    val RedBright = Color(0xFFEF9A9A)
    val OrangeBright = Color(0xFFFE8019)
    val YellowBright = Color(0xFFE9C46A)
    val GreenBright = Color(0xFFC5D68D)
    val SageBright = Color(0xFFA3B495)  // Bright sage (replaces bright blue)
    val AquaBright = Color(0xFFA3C9A8)
    val PurpleBright = Color(0xFFE0A1B5)
}

/**
 * Extended color palette for terminal-specific needs beyond Material3.
 */
@Immutable
data class NovaTermColors(
    val terminalBackground: Color = Ember.Bg0Hard,
    val terminalForeground: Color = Ember.Fg1,
    val tabBarBackground: Color = Ember.Bg0,
    val destructive: Color = Ember.Red,
    val accent: Color = Ember.Orange,
    val cursor: Color = Ember.Orange,
    val selection: Color = Color(0xFF45403D),
)

val LocalNovaTermColors = staticCompositionLocalOf { NovaTermColors() }

// ── Material3 color schemes ────────────────────────────────

private val EmberDark = darkColorScheme(
    primary = Ember.Orange,
    onPrimary = Ember.Bg0Hard,
    primaryContainer = Ember.Bg1,
    onPrimaryContainer = Ember.Fg1,
    secondary = Ember.Aqua,
    onSecondary = Ember.Bg0Hard,
    secondaryContainer = Ember.Bg2,
    onSecondaryContainer = Ember.Fg1,
    tertiary = Ember.Sage,           // Was blue — now warm sage
    onTertiary = Ember.Bg0Hard,
    tertiaryContainer = Ember.Bg2,
    onTertiaryContainer = Ember.Fg1,
    background = Ember.Bg0Hard,
    onBackground = Ember.Fg1,
    surface = Ember.Bg0,
    onSurface = Ember.Fg1,
    surfaceVariant = Ember.Bg1,
    onSurfaceVariant = Ember.Fg4,
    surfaceContainerHighest = Ember.Bg2,
    outline = Ember.Bg4,
    outlineVariant = Ember.Bg3,
    error = Ember.Red,
    onError = Ember.Bg0Hard,
    errorContainer = Color(0xFF442726),
    onErrorContainer = Ember.RedBright,
    inverseSurface = Ember.Fg1,
    inverseOnSurface = Ember.Bg0,
    inversePrimary = Color(0xFFAF5D0E),
    scrim = Color.Black,
)

private val EmberLight = lightColorScheme(
    primary = Color(0xFFAF5D0E),
    onPrimary = Color(0xFFFBF1C7),
    primaryContainer = Color(0xFFEBDBB2),
    onPrimaryContainer = Color(0xFF3C3836),
    secondary = Color(0xFF427B58),
    onSecondary = Color(0xFFFBF1C7),
    secondaryContainer = Color(0xFFD5C4A1),
    onSecondaryContainer = Color(0xFF3C3836),
    tertiary = Color(0xFF5B7A52),     // Warm sage for light
    onTertiary = Color(0xFFFBF1C7),
    tertiaryContainer = Color(0xFFD5C4A1),
    onTertiaryContainer = Color(0xFF3C3836),
    background = Color(0xFFFBF1C7),
    onBackground = Color(0xFF3C3836),
    surface = Color(0xFFF9F5D7),
    onSurface = Color(0xFF3C3836),
    surfaceVariant = Color(0xFFEBDBB2),
    onSurfaceVariant = Color(0xFF7C6F64),
    outline = Color(0xFF7C6F64),
    outlineVariant = Color(0xFFD5C4A1),
    error = Color(0xFFCC241D),
    onError = Color(0xFFFBF1C7),
    errorContainer = Color(0xFFFCDCD7),
    onErrorContainer = Color(0xFFCC241D),
    inverseSurface = Color(0xFF3C3836),
    inverseOnSurface = Color(0xFFFBF1C7),
    inversePrimary = Ember.OrangeBright,
    scrim = Color.Black,
)

// ── Theme composable ───────────────────────────────────────

@Composable
fun NovaTermTheme(
    darkTheme: Boolean = true, // Terminal apps default to dark
    dynamicColor: Boolean = false, // Ember palette by default
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> EmberDark
        else -> EmberLight
    }

    val novaTermColors = if (darkTheme) {
        NovaTermColors()
    } else {
        NovaTermColors(
            terminalBackground = Color(0xFFF9F5D7),
            terminalForeground = Color(0xFF3C3836),
            tabBarBackground = Color(0xFFFBF1C7),
            destructive = Color(0xFFCC241D),
            accent = Color(0xFFAF5D0E),
            cursor = Color(0xFFAF5D0E),
            selection = Color(0xFFD5C4A1),
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
