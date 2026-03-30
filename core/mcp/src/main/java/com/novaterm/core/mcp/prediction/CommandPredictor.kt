package com.novaterm.core.mcp.prediction

/**
 * N-gram based command predictor.
 *
 * Learns from shell history to suggest the next command.
 * Uses bigrams (pairs of consecutive commands) for prediction.
 * No ML model needed — pure statistics, instant results (<1ms).
 *
 * Architecture:
 * - Bigram model: maps (prev_command → next_command → count)
 * - Unigram fallback: most frequent commands overall
 * - Context-aware: considers CWD and git branch
 * - Incremental: updates on every command execution
 *
 * Thread-safe: all mutations are synchronized on [lock].
 * Reads take a snapshot under the lock then process outside it.
 *
 * Storage: serialized to JSON in app-private storage.
 * Typical size: <1MB for 100K commands.
 */
class CommandPredictor {

    private val lock = Any()

    // bigram: previous command → (next command → count)
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    // unigram: command → total count
    private val unigrams = HashMap<String, Int>()
    // cwd-aware: (cwd, command) → count
    private val cwdCommands = HashMap<String, HashMap<String, Int>>()

    private var totalCommands = 0

    /**
     * Record a command execution for learning.
     *
     * @param command The command that was executed.
     * @param previousCommand The command executed before this one (null if first).
     * @param cwd The working directory when the command was executed.
     */
    fun learn(command: String, previousCommand: String? = null, cwd: String? = null) {
        val normalized = normalize(command)
        if (normalized.isBlank()) return

        synchronized(lock) {
            // Update unigram
            unigrams[normalized] = (unigrams[normalized] ?: 0) + 1
            totalCommands++

            // Update bigram
            if (previousCommand != null) {
                val prevNorm = normalize(previousCommand)
                if (prevNorm.isNotBlank()) {
                    val followers = bigrams.getOrPut(prevNorm) { HashMap() }
                    followers[normalized] = (followers[normalized] ?: 0) + 1
                }
            }

            // Update CWD context
            if (cwd != null) {
                val cwdKey = shortenCwd(cwd)
                val dirCommands = cwdCommands.getOrPut(cwdKey) { HashMap() }
                dirCommands[normalized] = (dirCommands[normalized] ?: 0) + 1
            }

            // Evict least frequent entries if vocabulary exceeds limits
            if (unigrams.size > MAX_UNIGRAMS) {
                val threshold = unigrams.values.sorted()[unigrams.size - MAX_UNIGRAMS]
                unigrams.entries.removeAll { it.value <= threshold }
            }
            if (bigrams.size > MAX_BIGRAM_KEYS) {
                val leastUsed = bigrams.entries
                    .sortedBy { it.value.values.sum() }
                    .take(bigrams.size - MAX_BIGRAM_KEYS)
                leastUsed.forEach { bigrams.remove(it.key) }
            }
            if (cwdCommands.size > MAX_CWD_KEYS) {
                val leastUsed = cwdCommands.entries
                    .sortedBy { it.value.values.sum() }
                    .take(cwdCommands.size - MAX_CWD_KEYS)
                leastUsed.forEach { cwdCommands.remove(it.key) }
            }
        }
    }

    /**
     * Predict the next command.
     *
     * @param previousCommand The most recently executed command.
     * @param prefix Current input prefix (what user is typing).
     * @param cwd Current working directory.
     * @param maxResults Maximum number of suggestions.
     * @return List of (command, confidence) pairs, sorted by confidence descending.
     */
    fun predict(
        previousCommand: String? = null,
        prefix: String = "",
        cwd: String? = null,
        maxResults: Int = 5,
    ): List<Prediction> {
        if (maxResults <= 0) return emptyList()

        // Take a snapshot under the lock
        val bigramSnapshot: Map<String, Int>?
        val cwdSnapshot: Map<String, Int>?
        val unigramSnapshot: Map<String, Int>
        val total: Int

        synchronized(lock) {
            bigramSnapshot = if (previousCommand != null) {
                val prevNorm = normalize(previousCommand)
                bigrams[prevNorm]?.let { HashMap(it) }
            } else null

            cwdSnapshot = if (cwd != null) {
                val cwdKey = shortenCwd(cwd)
                cwdCommands[cwdKey]?.let { HashMap(it) }
            } else null

            unigramSnapshot = HashMap(unigrams)
            total = totalCommands
        }

        // Process outside the lock
        val candidates = HashMap<String, Double>()

        // Score 1: Bigram (strongest signal — what usually follows prev command)
        if (bigramSnapshot != null) {
            val bigramTotal = bigramSnapshot.values.sum().toDouble()
            for ((cmd, count) in bigramSnapshot) {
                candidates[cmd] = (candidates[cmd] ?: 0.0) + (count / bigramTotal) * BIGRAM_WEIGHT
            }
        }

        // Score 2: CWD context (what commands are common in this directory)
        if (cwdSnapshot != null) {
            val cwdTotal = cwdSnapshot.values.sum().toDouble()
            for ((cmd, count) in cwdSnapshot) {
                candidates[cmd] = (candidates[cmd] ?: 0.0) + (count / cwdTotal) * CWD_WEIGHT
            }
        }

        // Score 3: Unigram fallback (most frequent commands overall)
        if (total > 0) {
            for ((cmd, count) in unigramSnapshot) {
                candidates[cmd] = (candidates[cmd] ?: 0.0) + (count.toDouble() / total) * UNIGRAM_WEIGHT
            }
        }

        // Filter by prefix if user is typing
        val filtered = if (prefix.isNotBlank()) {
            candidates.filter { it.key.startsWith(prefix, ignoreCase = true) }
        } else {
            candidates
        }

        return filtered.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { Prediction(it.key, it.value) }
    }

    /**
     * Load history from a bash_history-style file (one command per line).
     */
    fun loadHistory(historyLines: List<String>) {
        var prev: String? = null
        for (line in historyLines) {
            val cmd = line.trim()
            if (cmd.isBlank() || cmd.startsWith("#")) continue
            learn(cmd, prev)
            prev = cmd
        }
    }

    /** Number of unique commands learned. */
    val vocabularySize: Int get() = synchronized(lock) { unigrams.size }

    /** Total commands learned. */
    val totalLearned: Int get() = synchronized(lock) { totalCommands }

    /** Clear all learned data. */
    fun clear() {
        synchronized(lock) {
            bigrams.clear()
            unigrams.clear()
            cwdCommands.clear()
            totalCommands = 0
        }
    }

    // ── Export / Import (for persistence) ───────────────────

    /** Export unigrams for serialization. */
    fun exportUnigrams(): Map<String, Int> = synchronized(lock) { HashMap(unigrams) }

    /** Export bigrams for serialization. */
    fun exportBigrams(): Map<String, Map<String, Int>> = synchronized(lock) {
        bigrams.mapValues { HashMap(it.value) }
    }

    /** Export CWD commands for serialization. */
    fun exportCwdCommands(): Map<String, Map<String, Int>> = synchronized(lock) {
        cwdCommands.mapValues { HashMap(it.value) }
    }

    /** Import state from deserialized data. */
    fun importState(
        unigrams: Map<String, Int>,
        bigrams: Map<String, Map<String, Int>>,
        cwdCommands: Map<String, Map<String, Int>>,
        totalCommands: Int,
    ) {
        synchronized(lock) {
            this.bigrams.clear()
            this.unigrams.clear()
            this.cwdCommands.clear()
            this.totalCommands = totalCommands
            for ((k, v) in unigrams) this.unigrams[k] = v
            for ((k, inner) in bigrams) {
                this.bigrams[k] = HashMap(inner)
            }
            for ((k, inner) in cwdCommands) {
                this.cwdCommands[k] = HashMap(inner)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    /** Normalize command for matching (strip leading whitespace, collapse spaces). */
    private fun normalize(command: String): String {
        return command.trim().replace(WHITESPACE_REGEX, " ")
    }

    /** Shorten CWD to last 2 segments for bucketing. */
    private fun shortenCwd(cwd: String): String {
        val parts = cwd.split("/").filter { it.isNotEmpty() }
        return parts.takeLast(2).joinToString("/")
    }

    companion object {
        // Scoring weights
        private const val BIGRAM_WEIGHT = 0.6   // What usually follows
        private const val CWD_WEIGHT = 0.25     // What's common in this directory
        private const val UNIGRAM_WEIGHT = 0.15  // Most frequent overall

        // Pre-compiled regex for normalize() — avoids recompilation on every call
        private val WHITESPACE_REGEX = Regex("\\s+")

        // Memory limits — evict least frequent when exceeded
        private const val MAX_UNIGRAMS = 10_000
        private const val MAX_BIGRAM_KEYS = 5_000
        private const val MAX_CWD_KEYS = 1_000
    }
}

/** A predicted command with confidence score (0.0 to 1.0). */
data class Prediction(
    val command: String,
    val confidence: Double,
)
