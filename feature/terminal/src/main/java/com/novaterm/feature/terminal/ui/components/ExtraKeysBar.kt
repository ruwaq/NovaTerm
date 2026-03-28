package com.novaterm.feature.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ExtraKey(
    val label: String,
    val code: String = label,
    val popup: String? = null,
    val isModifier: Boolean = false,
)

private val ROW_1 = listOf(
    ExtraKey("ESC", "\u001b"),
    ExtraKey("TAB", "\t"),
    ExtraKey("/"),
    ExtraKey("-"),
    ExtraKey("~"),
    ExtraKey("|"),
    ExtraKey("UP", "\u001b[A", popup = "PgUp"),
    ExtraKey("DOWN", "\u001b[B", popup = "PgDn"),
    ExtraKey("ENTER", "\r"),
)

private val ROW_2 = listOf(
    ExtraKey("CTRL", isModifier = true),
    ExtraKey("ALT", isModifier = true),
    ExtraKey("LEFT", "\u001b[D", popup = "Home"),
    ExtraKey("RIGHT", "\u001b[C", popup = "End"),
    ExtraKey("{"),
    ExtraKey("}"),
    ExtraKey("["),
    ExtraKey("]"),
    ExtraKey("_"),
)

@Composable
fun ExtraKeysBar(
    onKey: (String) -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    ctrlActive: Boolean = false,
    altActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1D2021))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ExtraKeyRow(ROW_1, onKey, onCtrlToggle, onAltToggle, ctrlActive, altActive, haptic)
        ExtraKeyRow(ROW_2, onKey, onCtrlToggle, onAltToggle, ctrlActive, altActive, haptic)
    }
}

@Composable
private fun ExtraKeyRow(
    keys: List<ExtraKey>,
    onKey: (String) -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    ctrlActive: Boolean,
    altActive: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        keys.forEach { key ->
            val isActive = when (key.label) {
                "CTRL" -> ctrlActive
                "ALT" -> altActive
                else -> false
            }

            ExtraKeyButton(
                key = key,
                isActive = isActive,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (key.label) {
                        "CTRL" -> onCtrlToggle()
                        "ALT" -> onAltToggle()
                        else -> onKey(key.code)
                    }
                },
                onLongClick = {
                    if (key.popup != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        when (key.popup) {
                            "PgUp" -> onKey("\u001b[5~")
                            "PgDn" -> onKey("\u001b[6~")
                            "Home" -> onKey("\u001b[H")
                            "End" -> onKey("\u001b[F")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ExtraKeyButton(
    key: ExtraKey,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    val bgColor = when {
        isActive -> Color(0xFFFE8019)
        pressed -> Color(0xFF504945)
        key.isModifier -> Color(0xFF3C3836)
        else -> Color(0xFF282828)
    }

    val textColor = when {
        isActive -> Color(0xFF1D2021)
        else -> Color(0xFFEBDBB2)
    }

    Box(
        modifier = Modifier
            .height(42.dp)
            .widthIn(min = 42.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = key.label,
                color = textColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (key.popup != null) {
                Text(
                    text = key.popup,
                    color = textColor.copy(alpha = 0.4f),
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
