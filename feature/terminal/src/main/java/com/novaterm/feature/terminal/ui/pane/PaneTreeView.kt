package com.novaterm.feature.terminal.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.novaterm.feature.terminal.ui.screen.TerminalScreen
import com.novaterm.terminal.TerminalSession
import com.novaterm.view.TerminalView

/**
 * Recursively renders a [PaneNode] tree as nested Row/Column splits.
 *
 * Each [PaneNode.Leaf] renders a [TerminalScreen] for its session.
 * Each [PaneNode.Split] renders children in a [Row] (horizontal) or [Column] (vertical)
 * separated by a thin divider.
 *
 * The focused pane gets a subtle border highlight so the user knows which pane
 * receives keyboard input.
 */
@Composable
fun PaneTreeView(
    node: PaneNode,
    sessions: List<TerminalSession>,
    focusedSessionIndex: Int,
    fontSize: Int,
    fontFamily: String,
    colorScheme: String,
    keepScreenOn: Boolean,
    ctrlActive: Boolean,
    altActive: Boolean,
    backIsEscape: Boolean,
    onModifiersConsumed: () -> Unit,
    onFocusPane: (sessionIndex: Int) -> Unit,
    onBlockComplete: ((sessionIndex: Int, command: String, exitCode: Int?) -> Unit)?,
    onTabAcceptSuggestion: (() -> String?)?,
    onViewReady: ((sessionIndex: Int, TerminalView) -> Unit)?,
    onPromptNavigatorReady: ((jumpToPrompt: (Int) -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is PaneNode.Leaf -> {
            val sessionIndex = node.sessionIndex
            if (sessionIndex !in sessions.indices) {
                // Session not available yet — render empty placeholder
                Box(modifier = modifier.fillMaxSize())
                return
            }

            val session = sessions[sessionIndex]
            val isFocused = sessionIndex == focusedSessionIndex
            val focusBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isFocused) 0.6f else 0f)

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(focusBorderColor, RoundedCornerShape(2.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onFocusPane(sessionIndex)
                    },
            ) {
                TerminalScreen(
                    session = session,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    colorScheme = colorScheme,
                    keepScreenOn = keepScreenOn && isFocused,
                    ctrlActive = if (isFocused) ctrlActive else false,
                    altActive = if (isFocused) altActive else false,
                    backIsEscape = backIsEscape,
                    onModifiersConsumed = onModifiersConsumed,
                    onBlockComplete = { command, exitCode ->
                        onBlockComplete?.invoke(sessionIndex, command, exitCode)
                    },
                    onTabAcceptSuggestion = if (isFocused) onTabAcceptSuggestion else null,
                    onViewReady = { view -> onViewReady?.invoke(sessionIndex, view) },
                    onPromptNavigatorReady = if (isFocused) onPromptNavigatorReady else null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        is PaneNode.Split -> {
            val dividerColor = MaterialTheme.colorScheme.outlineVariant

            when (node.direction) {
                SplitDirection.HORIZONTAL -> {
                    Row(modifier = modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(node.ratio).fillMaxHeight()) {
                            PaneTreeView(
                                node = node.first,
                                sessions = sessions,
                                focusedSessionIndex = focusedSessionIndex,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                colorScheme = colorScheme,
                                keepScreenOn = keepScreenOn,
                                ctrlActive = ctrlActive,
                                altActive = altActive,
                                backIsEscape = backIsEscape,
                                onModifiersConsumed = onModifiersConsumed,
                                onFocusPane = onFocusPane,
                                onBlockComplete = onBlockComplete,
                                onTabAcceptSuggestion = onTabAcceptSuggestion,
                                onViewReady = onViewReady,
                                onPromptNavigatorReady = onPromptNavigatorReady,
                            )
                        }

                        // Vertical divider between left and right panes
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(dividerColor),
                        )

                        Box(modifier = Modifier.weight(1f - node.ratio).fillMaxHeight()) {
                            PaneTreeView(
                                node = node.second,
                                sessions = sessions,
                                focusedSessionIndex = focusedSessionIndex,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                colorScheme = colorScheme,
                                keepScreenOn = keepScreenOn,
                                ctrlActive = ctrlActive,
                                altActive = altActive,
                                backIsEscape = backIsEscape,
                                onModifiersConsumed = onModifiersConsumed,
                                onFocusPane = onFocusPane,
                                onBlockComplete = onBlockComplete,
                                onTabAcceptSuggestion = onTabAcceptSuggestion,
                                onViewReady = onViewReady,
                                onPromptNavigatorReady = onPromptNavigatorReady,
                            )
                        }
                    }
                }

                SplitDirection.VERTICAL -> {
                    Column(modifier = modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(node.ratio).fillMaxWidth()) {
                            PaneTreeView(
                                node = node.first,
                                sessions = sessions,
                                focusedSessionIndex = focusedSessionIndex,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                colorScheme = colorScheme,
                                keepScreenOn = keepScreenOn,
                                ctrlActive = ctrlActive,
                                altActive = altActive,
                                backIsEscape = backIsEscape,
                                onModifiersConsumed = onModifiersConsumed,
                                onFocusPane = onFocusPane,
                                onBlockComplete = onBlockComplete,
                                onTabAcceptSuggestion = onTabAcceptSuggestion,
                                onViewReady = onViewReady,
                                onPromptNavigatorReady = onPromptNavigatorReady,
                            )
                        }

                        // Horizontal divider between top and bottom panes
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .fillMaxWidth()
                                .background(dividerColor),
                        )

                        Box(modifier = Modifier.weight(1f - node.ratio).fillMaxWidth()) {
                            PaneTreeView(
                                node = node.second,
                                sessions = sessions,
                                focusedSessionIndex = focusedSessionIndex,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                colorScheme = colorScheme,
                                keepScreenOn = keepScreenOn,
                                ctrlActive = ctrlActive,
                                altActive = altActive,
                                backIsEscape = backIsEscape,
                                onModifiersConsumed = onModifiersConsumed,
                                onFocusPane = onFocusPane,
                                onBlockComplete = onBlockComplete,
                                onTabAcceptSuggestion = onTabAcceptSuggestion,
                                onViewReady = onViewReady,
                                onPromptNavigatorReady = onPromptNavigatorReady,
                            )
                        }
                    }
                }
            }
        }
    }
}
