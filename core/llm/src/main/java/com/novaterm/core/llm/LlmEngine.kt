package com.novaterm.core.llm

import kotlinx.coroutines.flow.StateFlow

/**
 * On-device LLM engine interface.
 *
 * Completely optional — the user must explicitly enable it in Settings
 * and download the model. When disabled, the app falls back to the
 * N-gram PredictionEngine.
 *
 * Architecture:
 * - Model lives on disk (user-downloaded, ~288MB for Gemma 270M)
 * - Loaded into memory only when enabled (~551MB RAM)
 * - Inference runs on a background thread (coroutines)
 * - Results feed into the existing SuggestionBar UI
 */
interface LlmEngine {

    /** Current state of the engine. */
    val state: StateFlow<LlmState>

    /** Load the model into memory. Call from a background thread. */
    suspend fun initialize(): Boolean

    /**
     * Generate a command suggestion based on terminal context.
     *
     * @param context Terminal context (recent commands, CWD, errors).
     * @return Suggestion or null if inference fails/times out.
     */
    suspend fun suggest(context: TerminalContext): LlmSuggestion?

    /**
     * Explain a command or error message.
     *
     * @param input The command or error text to explain.
     * @param context Terminal context for better understanding.
     * @return Human-readable explanation.
     */
    suspend fun explain(input: String, context: TerminalContext): String?

    /** Release model from memory. */
    fun release()
}

/** State machine for the LLM engine lifecycle. */
enum class LlmState {
    /** Not initialized — model not loaded. */
    IDLE,
    /** Model is loading into memory. */
    LOADING,
    /** Ready for inference. */
    READY,
    /** Running inference. */
    INFERRING,
    /** Model file not found on disk. */
    MODEL_NOT_FOUND,
    /** Initialization failed (corrupt model, OOM, etc). */
    ERROR,
}

/** Terminal context passed to the LLM for inference. */
data class TerminalContext(
    /** Recent commands with exit codes (most recent last). */
    val recentCommands: List<CommandEntry> = emptyList(),
    /** Current working directory. */
    val cwd: String? = null,
    /** What the user is currently typing (prefix). */
    val currentPrefix: String = "",
    /** Last command's output (truncated to ~500 chars). */
    val lastOutput: String? = null,
)

/** A command from history with its result. */
data class CommandEntry(
    val command: String,
    val exitCode: Int? = null,
    val cwd: String? = null,
)

/** LLM-generated suggestion. */
data class LlmSuggestion(
    /** The suggested command. */
    val command: String,
    /** Confidence score (0.0 to 1.0). */
    val confidence: Double,
    /** Brief explanation of why this was suggested. */
    val reasoning: String? = null,
)
