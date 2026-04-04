package com.novaterm.core.llm

/**
 * Builds prompts for the on-device LLM from terminal context.
 *
 * Supports multiple prompt formats:
 * - Gemma 4: native system prompt + function calling support
 * - Gemma 3: <start_of_turn> instruction template
 * - ChatML: for SmolLM, Qwen, and other ChatML-compatible models
 *
 * Keeps prompts short (<512 tokens) for fast inference on device.
 */
object PromptBuilder {

    /**
     * Build a suggestion prompt from terminal context.
     * Selects format based on model family.
     */
    fun buildSuggestionPrompt(
        context: TerminalContext,
        family: ModelCatalog.ModelFamily = ModelCatalog.ModelFamily.GEMMA4,
    ): String {
        return when (family) {
            ModelCatalog.ModelFamily.GEMMA4 -> buildGemma4SuggestionPrompt(context)
            ModelCatalog.ModelFamily.GEMMA -> buildGemmaSuggestionPrompt(context)
            ModelCatalog.ModelFamily.SMOLLM,
            ModelCatalog.ModelFamily.QWEN -> buildChatMlSuggestionPrompt(context)
        }
    }

    /**
     * Build an explanation prompt for a command or error.
     */
    fun buildExplanationPrompt(
        input: String,
        context: TerminalContext,
        family: ModelCatalog.ModelFamily = ModelCatalog.ModelFamily.GEMMA4,
    ): String {
        return when (family) {
            ModelCatalog.ModelFamily.GEMMA4 -> buildGemma4ExplanationPrompt(input, context)
            ModelCatalog.ModelFamily.GEMMA -> buildGemmaExplanationPrompt(input, context)
            ModelCatalog.ModelFamily.SMOLLM,
            ModelCatalog.ModelFamily.QWEN -> buildChatMlExplanationPrompt(input, context)
        }
    }

    // ── Gemma 4 format (native system prompt) ───────────────

    private fun buildGemma4SuggestionPrompt(context: TerminalContext): String {
        val sb = StringBuilder()

        // System prompt (Gemma 4 native support)
        sb.append("<start_of_turn>system\n")
        sb.append("You are a terminal command assistant. ")
        sb.append("Respond with ONLY a single shell command, no explanation.\n")
        sb.append("<end_of_turn>\n")

        // User turn with context
        sb.append("<start_of_turn>user\n")
        appendTerminalContext(sb, context)
        sb.append("Suggest the next command.\n")
        sb.append("<end_of_turn>\n")

        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun buildGemma4ExplanationPrompt(input: String, context: TerminalContext): String {
        val sb = StringBuilder()

        sb.append("<start_of_turn>system\n")
        sb.append("You are a terminal expert. Explain commands and errors briefly (1-2 sentences).\n")
        sb.append("<end_of_turn>\n")

        sb.append("<start_of_turn>user\n")
        sb.append("Explain this:\n```\n${sanitize(input)}\n```\n")
        if (context.cwd != null) {
            sb.append("CWD: ${context.cwd}\n")
        }
        sb.append("<end_of_turn>\n")

        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    // ── Gemma 3 format ──────────────────────────────────────

    private fun buildGemmaSuggestionPrompt(context: TerminalContext): String {
        val sb = StringBuilder()

        sb.append("<start_of_turn>user\n")
        sb.append("You are a terminal assistant. Suggest the next shell command.\n")
        appendTerminalContext(sb, context)
        sb.append("Respond with ONLY the suggested command, nothing else.\n")
        sb.append("<end_of_turn>\n")

        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun buildGemmaExplanationPrompt(input: String, context: TerminalContext): String {
        val sb = StringBuilder()

        sb.append("<start_of_turn>user\n")
        sb.append("Explain this briefly (1-2 sentences):\n")
        sb.append("```\n${sanitize(input)}\n```\n")
        if (context.cwd != null) {
            sb.append("Context: CWD=${context.cwd}\n")
        }
        sb.append("<end_of_turn>\n")

        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    // ── ChatML format (SmolLM, Qwen) ────────────────────────

    private fun buildChatMlSuggestionPrompt(context: TerminalContext): String {
        val sb = StringBuilder()

        sb.append("<|im_start|>system\n")
        sb.append("You are a terminal command assistant. Respond with ONLY a single shell command.\n")
        sb.append("<|im_end|>\n")

        sb.append("<|im_start|>user\n")
        appendTerminalContext(sb, context)
        sb.append("Suggest the next command.\n")
        sb.append("<|im_end|>\n")

        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun buildChatMlExplanationPrompt(input: String, context: TerminalContext): String {
        val sb = StringBuilder()

        sb.append("<|im_start|>system\n")
        sb.append("Explain commands and errors briefly (1-2 sentences).\n")
        sb.append("<|im_end|>\n")

        sb.append("<|im_start|>user\n")
        sb.append("Explain: ${sanitize(input)}\n")
        sb.append("<|im_end|>\n")

        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    // ── Shared context builder ───────────────────────────────

    private fun appendTerminalContext(sb: StringBuilder, context: TerminalContext) {
        if (context.cwd != null) {
            sb.append("CWD: ${context.cwd}\n")
        }
        if (context.recentCommands.isNotEmpty()) {
            sb.append("Recent commands:\n")
            for (entry in context.recentCommands.takeLast(MAX_HISTORY)) {
                val exitStr = entry.exitCode?.let { " [exit=$it]" } ?: ""
                sb.append("$ ${sanitize(entry.command)}$exitStr\n")
            }
        }
        if (context.lastOutput != null) {
            val truncated = sanitize(context.lastOutput.take(MAX_OUTPUT_CHARS))
            sb.append("Last output:\n$truncated\n")
        }
        if (context.currentPrefix.isNotBlank()) {
            sb.append("User is typing: ${context.currentPrefix}\n")
        }
    }

    // ── Response parsing ─────────────────────────────────────

    /**
     * Parse LLM response to extract just the command.
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

        if (cleaned.isNullOrBlank() || cleaned.length > 500) return null
        return cleaned
    }

    private const val MAX_HISTORY = 8
    private const val MAX_OUTPUT_CHARS = 400

    /** Strip control tokens to prevent prompt injection via command history. */
    private fun sanitize(input: String): String {
        return input
            .replace("<start_of_turn>", "")
            .replace("<end_of_turn>", "")
            .replace("<start_of_role>", "")
            .replace("<end_of_role>", "")
            .replace("<|im_start|>", "")
            .replace("<|im_end|>", "")
    }
}
