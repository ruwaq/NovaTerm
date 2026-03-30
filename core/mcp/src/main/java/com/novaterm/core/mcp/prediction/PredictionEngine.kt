package com.novaterm.core.mcp.prediction

import java.io.File

/**
 * Manages the command prediction lifecycle.
 *
 * Bridges terminal command execution (via onBlockComplete callbacks)
 * with the N-gram predictor. Handles persistence (load on start, save periodically).
 *
 * Usage:
 * 1. Create PredictionEngine with app-private files dir.
 * 2. Call [onCommandExecuted] from onBlockComplete callback.
 * 3. Call [predict] to get suggestions.
 * 4. Call [save] periodically (e.g., every 30s with session save).
 */
class PredictionEngine(private val storageDir: File) {

    val predictor = CommandPredictor()

    // Track last command per session for bigram context (thread-safe)
    private val lastCommand = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile private var dirty = false

    /** Load saved model from disk. Call once on startup. */
    fun load() {
        val restored = CommandPredictorStore.load(storageDir)
        predictor.importState(
            unigrams = restored.exportUnigrams(),
            bigrams = restored.exportBigrams(),
            cwdCommands = restored.exportCwdCommands(),
            totalCommands = restored.totalLearned,
        )
    }

    /**
     * Feed a completed command to the predictor.
     * Call this from the onBlockComplete callback.
     *
     * @param sessionId Session where command was executed.
     * @param command The command string.
     * @param cwd Working directory at time of execution.
     */
    fun onCommandExecuted(sessionId: String, command: String, cwd: String? = null) {
        val prev = lastCommand[sessionId]
        predictor.learn(command, previousCommand = prev, cwd = cwd)
        lastCommand[sessionId] = command
        dirty = true
    }

    /**
     * Get command predictions.
     *
     * @param sessionId Session context (for bigram lookup).
     * @param prefix What the user is currently typing.
     * @param cwd Current working directory.
     * @param maxResults Maximum suggestions to return.
     */
    fun predict(
        sessionId: String? = null,
        prefix: String = "",
        cwd: String? = null,
        maxResults: Int = 5,
    ): List<Prediction> {
        val prev = sessionId?.let { lastCommand[it] }
        return predictor.predict(
            previousCommand = prev,
            prefix = prefix,
            cwd = cwd,
            maxResults = maxResults,
        )
    }

    /** Persist model to disk. Only writes if state has changed. Returns true on success. */
    fun save(): Boolean {
        if (!dirty) return true
        val success = CommandPredictorStore.save(predictor, storageDir)
        if (success) dirty = false
        return success
    }

    /**
     * Load bash_history file to bootstrap the model.
     * Useful for initial setup — learns from existing user history.
     *
     * @param historyFile Path to .bash_history or similar.
     */
    fun loadHistoryFile(historyFile: File) {
        if (!historyFile.exists()) return
        // Stream line-by-line to avoid loading entire history into memory
        var prev: String? = null
        historyFile.useLines { lines ->
            for (line in lines) {
                val cmd = line.trim()
                if (cmd.isBlank() || cmd.startsWith("#")) continue
                predictor.learn(cmd, prev)
                prev = cmd
            }
        }
        dirty = true
    }

    /** Remove session tracking when session is closed. */
    fun onSessionClosed(sessionId: String) {
        lastCommand.remove(sessionId)
    }

    /** Number of unique commands learned. */
    val vocabularySize: Int get() = predictor.vocabularySize

    /** Total commands learned. */
    val totalLearned: Int get() = predictor.totalLearned
}
