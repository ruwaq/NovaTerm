package com.novaterm.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Gruvbox-inspired palette
private val GruvboxDark = darkColorScheme(
    primary = Color(0xFFFE8019),
    onPrimary = Color(0xFF1D2021),
    primaryContainer = Color(0xFF3C3836),
    secondary = Color(0xFF8EC07C),
    onSecondary = Color(0xFF1D2021),
    tertiary = Color(0xFF83A598),
    background = Color(0xFF1D2021),
    surface = Color(0xFF282828),
    surfaceVariant = Color(0xFF3C3836),
    onBackground = Color(0xFFEBDBB2),
    onSurface = Color(0xFFEBDBB2),
    onSurfaceVariant = Color(0xFFA89984),
    error = Color(0xFFFB4934),
)

private val GruvboxLight = lightColorScheme(
    primary = Color(0xFFAF3A03),
    onPrimary = Color(0xFFFBF1C7),
    primaryContainer = Color(0xFFEBDBB2),
    secondary = Color(0xFF427B58),
    onSecondary = Color(0xFFFBF1C7),
    tertiary = Color(0xFF076678),
    background = Color(0xFFFBF1C7),
    surface = Color(0xFFF2E5BC),
    surfaceVariant = Color(0xFFEBDBB2),
    onBackground = Color(0xFF3C3836),
    onSurface = Color(0xFF3C3836),
    onSurfaceVariant = Color(0xFF665C54),
    error = Color(0xFFCC241D),
)

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

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
