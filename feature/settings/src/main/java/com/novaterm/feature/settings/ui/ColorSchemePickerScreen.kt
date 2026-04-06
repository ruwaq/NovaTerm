package com.novaterm.feature.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.res.stringResource
import com.novaterm.feature.settings.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class SchemePreview(
    val id: String,
    val name: String,
    val background: Color,
    val foreground: Color,
    val accent: Color,
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

// Terminal lines for the typing animation preview
private data class PreviewLine(
    val labelColor: Int, // index into scheme colors: 0=accent, 1=red, 2=green, 3=yellow, 4=blue, 5=cyan, 6=fg
    val label: String,
    val text: String,
)

private val PREVIEW_LINES = listOf(
    PreviewLine(0, "~$", " neofetch"),
    PreviewLine(6, "", ""),
    PreviewLine(2, "  OS", "      Android 15 aarch64"),
    PreviewLine(3, "  Host", "    Xiaomi 15T Pro"),
    PreviewLine(1, "  Shell", "   zsh 5.9"),
    PreviewLine(4, "  Term", "    NovaTerm"),
    PreviewLine(5, "  Uptime", "  2 hours, 15 mins"),
    PreviewLine(6, "", ""),
    PreviewLine(0, "~$", " echo \"Hello, World!\""),
    PreviewLine(6, "", "Hello, World!"),
    PreviewLine(0, "~$", ""),
)

/**
 * First-launch color scheme picker with live terminal preview.
 * Features a typewriter animation that types out terminal content,
 * smooth color transitions between schemes, and haptic feedback.
 */
@Composable
fun ColorSchemePickerScreen(
    onSchemeSelected: (String) -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val scheme = SCHEMES[selectedIndex]
    val haptic = LocalHapticFeedback.current

    // Typing animation state
    var visibleChars by remember { mutableIntStateOf(0) }
    val totalChars = remember { PREVIEW_LINES.sumOf { it.label.length + it.text.length + 1 } }

    // Typewriter effect: reveal characters one by one
    LaunchedEffect(Unit) {
        while (visibleChars < totalChars) {
            delay(18L) // ~55 chars/sec typing speed
            visibleChars++
        }
    }

    // Cursor blink
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursorBlink",
    )

    // Animate color transitions
    val bg by animateColorAsState(scheme.background, tween(300), label = "bg")
    val fg by animateColorAsState(scheme.foreground, tween(300), label = "fg")
    val accent by animateColorAsState(scheme.accent, tween(300), label = "accent")
    val red by animateColorAsState(scheme.red, tween(300), label = "red")
    val green by animateColorAsState(scheme.green, tween(300), label = "green")
    val yellow by animateColorAsState(scheme.yellow, tween(300), label = "yellow")
    val blue by animateColorAsState(scheme.blue, tween(300), label = "blue")
    val cyan by animateColorAsState(scheme.cyan, tween(300), label = "cyan")
    val magenta by animateColorAsState(scheme.magenta, tween(300), label = "magenta")

    fun colorForIndex(idx: Int): Color = when (idx) {
        0 -> accent; 1 -> red; 2 -> green; 3 -> yellow
        4 -> blue; 5 -> cyan; else -> fg
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_choose_theme),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.onboarding_change_later),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Live terminal preview with typing animation ───────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                var charsRemaining = visibleChars

                PREVIEW_LINES.forEach { line ->
                    val fullText = line.label + line.text
                    val lineVisible = charsRemaining.coerceAtMost(fullText.length)
                    charsRemaining -= lineVisible + 1 // +1 for newline

                    if (charsRemaining + lineVisible + 1 > 0) {
                        val visibleLabel = line.label.take(lineVisible)
                        val visibleText = if (lineVisible > line.label.length) {
                            line.text.take(lineVisible - line.label.length)
                        } else ""

                        val isLastVisible = charsRemaining <= 0 && visibleChars < totalChars

                        Row {
                            if (visibleLabel.isNotEmpty()) {
                                Text(
                                    text = visibleLabel,
                                    color = colorForIndex(line.labelColor),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 16.sp,
                                )
                            }
                            Text(
                                text = visibleText + if (isLastVisible) "█" else "",
                                color = if (isLastVisible) fg.copy(alpha = cursorAlpha) else fg,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                // Blinking cursor on last line after typing completes
                if (visibleChars >= totalChars) {
                    Text(
                        text = "█",
                        color = accent.copy(alpha = cursorAlpha),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Color palette blocks
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(red, green, yellow, blue, magenta, cyan).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 12.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Scheme name ───────────────────────────────────────
        Text(
            text = scheme.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Scheme selector chips ─────────────────────────────
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            itemsIndexed(SCHEMES) { index, item ->
                SchemeChip(
                    name = item.name,
                    background = item.background,
                    accent = item.accent,
                    foreground = item.foreground,
                    isSelected = index == selectedIndex,
                    onClick = {
                        if (index != selectedIndex) {
                            selectedIndex = index
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Continue button ───────────────────────────────────
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSchemeSelected(scheme.id)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_continue),
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SchemeChip(
    name: String,
    background: Color,
    accent: Color,
    foreground: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            }
            .then(
                if (isSelected) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(8.dp),
    ) {
        // Color preview circle — larger for touch
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (isSelected) Modifier.border(2.dp, accent, CircleShape)
                    else Modifier.border(1.dp, foreground.copy(alpha = 0.3f), CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ">_",
                color = accent,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(68.dp),
        )
    }
}
