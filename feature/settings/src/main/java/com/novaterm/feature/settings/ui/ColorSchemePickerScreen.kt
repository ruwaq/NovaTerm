package com.novaterm.feature.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Scheme definition for the picker: ID, display name, and key colors for preview.
 */
data class SchemePreview(
    val id: String,
    val name: String,
    val background: Color,
    val foreground: Color,
    val accent: Color,       // prompt/cursor color
    val red: Color,
    val green: Color,
    val yellow: Color,
    val blue: Color,
    val magenta: Color,
    val cyan: Color,
)

private val SCHEMES = listOf(
    SchemePreview(
        id = "gruvbox-dark", name = "Gruvbox Dark",
        background = Color(0xFF3C3836), foreground = Color(0xFFEBDBB2),
        accent = Color(0xFFE78A4E),
        red = Color(0xFFEA6962), green = Color(0xFFA9B665), yellow = Color(0xFFD8A657),
        blue = Color(0xFF7DAEA3), magenta = Color(0xFFD3869B), cyan = Color(0xFF89B482),
    ),
    SchemePreview(
        id = "gruvbox-light", name = "Gruvbox Light",
        background = Color(0xFFFBF1C7), foreground = Color(0xFF3C3836),
        accent = Color(0xFFAF5D0E),
        red = Color(0xFFCC241D), green = Color(0xFF98971A), yellow = Color(0xFFD79921),
        blue = Color(0xFF458588), magenta = Color(0xFFB16286), cyan = Color(0xFF689D6A),
    ),
    SchemePreview(
        id = "catppuccin-mocha", name = "Catppuccin Mocha",
        background = Color(0xFF1E1E2E), foreground = Color(0xFFCDD6F4),
        accent = Color(0xFFF5E0DC),
        red = Color(0xFFF38BA8), green = Color(0xFFA6E3A1), yellow = Color(0xFFF9E2AF),
        blue = Color(0xFF89B4FA), magenta = Color(0xFFF5C2E7), cyan = Color(0xFF94E2D5),
    ),
    SchemePreview(
        id = "solarized-dark", name = "Solarized Dark",
        background = Color(0xFF002B36), foreground = Color(0xFF839496),
        accent = Color(0xFF93A1A1),
        red = Color(0xFFDC322F), green = Color(0xFF859900), yellow = Color(0xFFB58900),
        blue = Color(0xFF268BD2), magenta = Color(0xFFD33682), cyan = Color(0xFF2AA198),
    ),
    SchemePreview(
        id = "monokai", name = "Monokai",
        background = Color(0xFF272822), foreground = Color(0xFFF8F8F2),
        accent = Color(0xFFF8F8F0),
        red = Color(0xFFF92672), green = Color(0xFFA6E22E), yellow = Color(0xFFF4BF75),
        blue = Color(0xFF66D9EF), magenta = Color(0xFFAE81FF), cyan = Color(0xFFA1EFE4),
    ),
    SchemePreview(
        id = "nord", name = "Nord",
        background = Color(0xFF2E3440), foreground = Color(0xFFD8DEE9),
        accent = Color(0xFFD8DEE9),
        red = Color(0xFFBF616A), green = Color(0xFFA3BE8C), yellow = Color(0xFFEBCB8B),
        blue = Color(0xFF81A1C1), magenta = Color(0xFFB48EAD), cyan = Color(0xFF88C0D0),
    ),
    SchemePreview(
        id = "dracula", name = "Dracula",
        background = Color(0xFF282A36), foreground = Color(0xFFF8F8F2),
        accent = Color(0xFFF8F8F2),
        red = Color(0xFFFF5555), green = Color(0xFF50FA7B), yellow = Color(0xFFF1FA8C),
        blue = Color(0xFFBD93F9), magenta = Color(0xFFFF79C6), cyan = Color(0xFF8BE9FD),
    ),
)

/**
 * First-launch color scheme picker with live terminal preview.
 * Shows a simulated terminal output that updates colors in real-time
 * as the user browses schemes.
 *
 * @param onSchemeSelected Called with the chosen scheme ID when user taps "Continue".
 */
@Composable
fun ColorSchemePickerScreen(
    onSchemeSelected: (String) -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val scheme = SCHEMES[selectedIndex]

    // Animate color transitions for smooth preview
    val bg by animateColorAsState(scheme.background, tween(300), label = "bg")
    val fg by animateColorAsState(scheme.foreground, tween(300), label = "fg")
    val accent by animateColorAsState(scheme.accent, tween(300), label = "accent")
    val red by animateColorAsState(scheme.red, tween(300), label = "red")
    val green by animateColorAsState(scheme.green, tween(300), label = "green")
    val yellow by animateColorAsState(scheme.yellow, tween(300), label = "yellow")
    val blue by animateColorAsState(scheme.blue, tween(300), label = "blue")
    val cyan by animateColorAsState(scheme.cyan, tween(300), label = "cyan")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Choose your theme",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You can change this later in Settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Live terminal preview ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Simulated terminal output
                TerminalLine(accent, fg, "~$", " neofetch")
                Spacer(modifier = Modifier.height(4.dp))
                TerminalLine(green, fg, "OS:", " Android 15 aarch64")
                TerminalLine(yellow, fg, "Host:", " Xiaomi 15T Pro")
                TerminalLine(red, fg, "Shell:", " zsh 5.9")
                TerminalLine(blue, fg, "Term:", " NovaTerm 0.1.0")
                TerminalLine(cyan, fg, "Uptime:", " 2 hours, 15 mins")
                Spacer(modifier = Modifier.height(8.dp))
                // Color test blocks
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        scheme.red, scheme.green, scheme.yellow,
                        scheme.blue, scheme.magenta, scheme.cyan,
                    ).forEach { color ->
                        val animatedColor by animateColorAsState(color, tween(300), label = "block")
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 14.dp)
                                .background(animatedColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TerminalLine(accent, fg, "~$", " echo \"Hello NovaTerm\"")
                TerminalLine(fg, fg, "", "Hello NovaTerm")
                TerminalLine(accent, fg, "~$", " █")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Scheme selector chips ─────────────────────────────
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(SCHEMES) { index, item ->
                SchemeChip(
                    name = item.name,
                    background = item.background,
                    accent = item.accent,
                    isSelected = index == selectedIndex,
                    onClick = { selectedIndex = index },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Continue button ───────────────────────────────────
        Button(
            onClick = { onSchemeSelected(scheme.id) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Continue with ${scheme.name}")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TerminalLine(
    labelColor: Color,
    textColor: Color,
    label: String,
    text: String,
) {
    Row {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SchemeChip(
    name: String,
    background: Color,
    accent: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(8.dp),
    ) {
        // Color preview circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(background)
                .border(1.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ">_",
                color = accent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(64.dp),
        )
    }
}
