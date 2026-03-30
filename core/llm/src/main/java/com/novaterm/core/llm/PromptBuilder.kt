package com.novaterm.core.llm

/**
 * Builds prompts for the on-device LLM from terminal context.
 *
 * Prompt format follows Gemma instruction tuning template:
 * <start_of_turn>user\n{prompt}<end_of_turn>\n<start_of_turn>model\n
 *
 * Keeps prompts short (<512 tokens) for fast inference on device.
 */
object PromptBuilder {

    /**
     * Build a suggestion prompt from terminal context.
     *
     * The LLM should respond with a single command, no explanation.
     */
    fun buildSuggestionPrompt(context: TerminalContext): String {
        val sb = StringBuilder()

        sb.append("<start_of_turn>user\n")
        sb.append("You are a terminal assistant. Suggest the next shell command.\n")

        // CWD context
        if (context.cwd != null) {
            sb.append("CWD: ${context.cwd}\n")
        }

        // Recent command history (sanitized to prevent prompt injection)
        if (context.recentCommands.isNotEmpty()) {
            sb.append("Recent commands:\n")
            for (entry in context.recentCommands.takeLast(MAX_HISTORY)) {
                val exitStr = entry.exitCode?.let { " [exit=$it]" } ?: ""
                sb.append("$ ${sanitize(entry.command)}$exitStr\n")
            }
        }

        // Last command output (sanitized, for error context)
        if (context.lastOutput != null) {
            val truncated = sanitize(context.lastOutput.take(MAX_OUTPUT_CHARS))
            sb.append("Last output:\n$truncated\n")
        }

        // Current prefix
        if (context.currentPrefix.isNotBlank()) {
            sb.append("User is typing: ${context.currentPrefix}\n")
        }

        sb.append("Respond with ONLY the suggested command, nothing else.\n")
        sb.append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")

        return sb.toString()
    }

    /**
     * Build an explanation prompt for a command or error.
     */
    fun buildExplanationPrompt(input: String, context: TerminalContext): String {
        val sb = StringBuilder()

        sb.append("<start_of_turn>user\n")
        sb.append("Explain this briefly (1-2 sentences):\n")
        sb.append("```\n$input\n```\n")

        if (context.cwd != null) {
            sb.append("Context: CWD=${context.cwd}\n")
        }

        sb.append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")

        return sb.toString()
    }

    /**
     * Parse LLM response to extract just the command.
     *
     * Strips markdown formatting, explanations, and trailing whitespace.
     */
    fun parseCommandResponse(response: String): String? {
        val cleaned = response
            .trim()
            .removePrefix("```bash").removePrefix("```sh").removePrefix("```")
            .removeSuffix("```")
            .trim()
            .lines()
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.removePrefix("$ ")
            ?.trim()

        // Reject empty, too short, or suspiciously long responses
        if (cleaned.isNullOrBlank() || cleaned.length > 500) return null

        return cleaned
    }

    private const val MAX_HISTORY = 8
    private const val MAX_OUTPUT_CHARS = 400

    /** Strip Gemma control tokens to prevent prompt injection via command history. */
    private fun sanitize(input: String): String {
        return input
            .replace("<start_of_turn>", "")
            .replace("<end_of_turn>", "")
            .replace("<start_of_role>", "")
            .replace("<end_of_role>", "")
    }
}
