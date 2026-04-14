package com.novaterm.core.session.manager

/**
 * Pre-configured template for launching an AI agent.
 *
 * Presets capture the command, arguments, environment variables,
 * and working directory needed to start a specific agent type.
 * Users can create custom presets or use the built-in ones.
 *
 * Stored in DataStore for persistence across app restarts.
 */
data class AgentPreset(
    val id: String,
    val name: String,
    val agentType: AgentType,
    val command: String,
    val args: List<String> = emptyList(),
    val envOverrides: Map<String, String> = emptyMap(),
    val workingDir: String = "",   // Empty = use home directory
    val isBuiltIn: Boolean = false,
    val icon: String = "",         // Material icon name
) {
    companion object {
        /** Built-in presets shipped with NovaTerm. */
        val BUILT_INS = listOf(
            AgentPreset(
                id = "claude-code",
                name = "Claude Code",
                agentType = AgentType.CLAUDE_CODE,
                command = "claude",
                args = listOf("--dangerously-skip-permissions"),
                envOverrides = mapOf(
                    "CLAUDE_CODE_SCROLL_SPEED" to "3",
                    "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC" to "1",
                ),
                isBuiltIn = true,
                icon = "smart_toy",
            ),
            AgentPreset(
                id = "gemini-cli",
                name = "Gemini CLI",
                agentType = AgentType.GEMINI_CLI,
                command = "gemini",
                isBuiltIn = true,
                icon = "auto_awesome",
            ),
            AgentPreset(
                id = "aider-claude",
                name = "Aider + Claude",
                agentType = AgentType.AIDER,
                command = "aider",
                args = listOf("--model", "anthropic/claude-sonnet-4-20250514", "--dark-mode"),
                isBuiltIn = true,
                icon = "code",
            ),
            AgentPreset(
                id = "opencode",
                name = "OpenCode",
                agentType = AgentType.OPENCODE,
                command = "opencode",
                isBuiltIn = true,
                icon = "terminal",
            ),
            AgentPreset(
                id = "aider-gemini",
                name = "Aider + Gemini",
                agentType = AgentType.AIDER,
                command = "aider",
                args = listOf("--model", "gemini/gemini-2.5-pro", "--dark-mode"),
                envOverrides = mapOf(
                    "GEMINI_API_KEY" to "",  // Filled from secure storage at launch
                ),
                isBuiltIn = true,
                icon = "code",
            ),
        )
    }
}