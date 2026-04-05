package com.novaterm.feature.settings.model

import com.novaterm.feature.settings.R

/**
 * Represents an AI coding tool that can be installed from the terminal.
 *
 * @param nameResId String resource ID for the tool's display name.
 * @param descResId String resource ID for the tool's description.
 * @param installCommand Shell command to install the tool.
 * @param checkCommand Shell command to check if the tool is installed.
 */
data class AiTool(
    val nameResId: Int,
    val descResId: Int,
    val installCommand: String,
    val checkCommand: String,
) {
    companion object {
        /** All available AI coding tools. */
        val ALL = listOf(
            AiTool(
                nameResId = R.string.settings_ai_tool_claude,
                descResId = R.string.settings_ai_tool_claude_desc,
                installCommand = "npm install -g @anthropic-ai/claude-code",
                checkCommand = "which claude",
            ),
            AiTool(
                nameResId = R.string.settings_ai_tool_gemini,
                descResId = R.string.settings_ai_tool_gemini_desc,
                installCommand = "npm install -g @google/gemini-cli",
                checkCommand = "which gemini",
            ),
            AiTool(
                nameResId = R.string.settings_ai_tool_aider,
                descResId = R.string.settings_ai_tool_aider_desc,
                installCommand = "pip install aider-install && aider-install",
                checkCommand = "which aider",
            ),
            AiTool(
                nameResId = R.string.settings_ai_tool_opencode,
                descResId = R.string.settings_ai_tool_opencode_desc,
                installCommand = "npm install -g opencode-ai",
                checkCommand = "which opencode",
            ),
        )
    }
}
