package com.novaterm.feature.settings.model

import com.novaterm.feature.settings.R

/**
 * Represents an AI coding tool that can be installed from the terminal.
 *
 * @param nameResId String resource ID for the tool's display name.
 * @param descResId String resource ID for the tool's description.
 * @param authMethod How the user authenticates after installation.
 * @param dependencies APT packages required before the tool can be installed.
 * @param installCommand Shell command to install the tool.
 * @param checkCommand Shell command to check if the tool is installed.
 * @param postInstallHint Optional hint shown after installation.
 */
data class AiTool(
    val id: String,
    val nameResId: Int,
    val descResId: Int,
    val installCommand: String,
    val checkCommand: String,
    val authMethod: AuthMethod = AuthMethod.API_KEY,
    val dependencies: List<String> = emptyList(),
    val postInstallHint: String? = null,
) {
    companion object {
        /** All available AI coding tools. */
        val ALL = listOf(
            AiTool(
                id = "claude",
                nameResId = R.string.settings_ai_tool_claude,
                descResId = R.string.settings_ai_tool_claude_desc,
                installCommand = "npm install -g @anthropic-ai/claude-code",
                checkCommand = "which claude",
                authMethod = AuthMethod.API_KEY,
                dependencies = listOf("nodejs"),
            ),
            AiTool(
                id = "gemini",
                nameResId = R.string.settings_ai_tool_gemini,
                descResId = R.string.settings_ai_tool_gemini_desc,
                installCommand = "npm install -g @google/gemini-cli",
                checkCommand = "which gemini",
                authMethod = AuthMethod.GOOGLE_LOGIN,
                dependencies = listOf("nodejs"),
                postInstallHint = "Run 'gemini auth login' to sign in with Google",
            ),
            AiTool(
                id = "aider",
                nameResId = R.string.settings_ai_tool_aider,
                descResId = R.string.settings_ai_tool_aider_desc,
                installCommand = "pip install aider-install && aider-install",
                checkCommand = "which aider",
                authMethod = AuthMethod.API_KEY_OR_LOCAL,
                dependencies = listOf("python", "python-pip"),
            ),
            AiTool(
                id = "opencode",
                nameResId = R.string.settings_ai_tool_opencode,
                descResId = R.string.settings_ai_tool_opencode_desc,
                installCommand = "npm install -g opencode-ai",
                checkCommand = "which opencode",
                authMethod = AuthMethod.FREE_TIER,
                dependencies = listOf("nodejs"),
            ),
            AiTool(
                id = "ollama",
                nameResId = R.string.settings_ai_tool_ollama,
                descResId = R.string.settings_ai_tool_ollama_desc,
                installCommand = "curl -fsSL https://ollama.com/install.sh | sh",
                checkCommand = "which ollama",
                authMethod = AuthMethod.LOCAL_NO_KEY,
                dependencies = emptyList(),
                postInstallHint = "Run 'ollama pull gemma:2b' to download a model",
            ),
        )
    }
}

/**
 * Authentication method required by an AI tool.
 */
enum class AuthMethod {
    API_KEY,
    GOOGLE_LOGIN,
    LOCAL_NO_KEY,
    API_KEY_OR_LOCAL,
    FREE_TIER,
}