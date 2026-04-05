package com.novaterm.core.mcp.security

/**
 * Security policy for MCP tool execution.
 *
 * Defines risk levels and validation rules. Tools declare their
 * risk level; the [ApprovalManager] uses the policy to decide
 * whether to auto-approve, prompt the user, or deny.
 */
object SecurityPolicy {

    /** Risk classification for MCP tools. */
    enum class RiskLevel {
        /** Read-only, no side effects. Auto-approved. */
        SAFE,
        /** Modifies state in a controlled way. Approval depends on config. */
        MODERATE,
        /** Arbitrary execution or file writes. Always requires approval. */
        DANGEROUS,
    }

    /**
     * Patterns checked against the FULL command (not split).
     * Used for constructs that themselves contain metacharacters (e.g., fork bomb).
     */
    private val BLOCKED_FULL_PATTERNS = listOf(
        Regex(""":\(\)\s*\{.*\|.*&\s*\}"""),   // fork bomb: :() { :|:& }
    )

    /**
     * Patterns checked per-segment after splitting on shell metacharacters.
     *
     * These only match the dangerous root-level invocations, not safe sub-paths.
     * For example: `rm -rf /` is blocked but `rm -rf /tmp/test` is not.
     *
     * Injection example:
     *   `cat file ; rm -rf /`  → split on `;` → segment `rm -rf /` → BLOCKED
     *   `echo ok && dd if=/dev/zero of=/dev/sda` → second segment → BLOCKED
     */
    private val BLOCKED_SEGMENT_PATTERNS = listOf(
        // rm -rf / or rm -rf /* — only root, not arbitrary sub-paths
        Regex("""^rm\s+(-[rRf]+\s+)+(/\s*$|/\*)"""),
        Regex("""^mkfs"""),                     // mkfs anything
        Regex("""^dd\s+if=/dev/"""),            // dd from raw device
        Regex("""^chmod\s+-R\s+777\s+/\s*$"""), // chmod -R 777 / (exact root)
    )

    // Split on all shell metacharacters that can chain commands or redirect I/O.
    // Missing any allows injection: cmd && evil, cmd | evil, cmd > file, etc.
    private val SHELL_METACHAR = Regex("""[;`&|<>\n]|\$\(|\$\{|&&|\|\|""")

    /** Paths that tools cannot read or write. */
    val BLOCKED_PATHS = setOf(
        "/system",
        "/vendor",
        "/proc",
        "/sys",
    )

    /** Check if a remote address is allowed to connect. */
    fun isAllowedOrigin(remoteAddress: String): Boolean {
        return remoteAddress in LOCALHOST_ADDRESSES
    }

    /**
     * Check if a command is blocked by policy.
     *
     * Two-pass approach:
     * 1. Full-command check for patterns that contain metacharacters (fork bomb).
     * 2. Per-segment check after splitting on `;`, `` ` ``, and `$(` to catch
     *    injection attacks like `cat file ; rm -rf /`.
     */
    fun isBlockedCommand(command: String): Boolean {
        val normalized = command.trim()

        // Pass 1: check the full command (fork bomb detection)
        if (BLOCKED_FULL_PATTERNS.any { it.containsMatchIn(normalized) }) return true

        // Pass 2: check each segment after splitting on injection metacharacters
        return normalized.split(SHELL_METACHAR)
            .any { segment ->
                BLOCKED_SEGMENT_PATTERNS.any { it.containsMatchIn(segment.trim()) }
            }
    }

    /** Check if a file path is blocked by policy. */
    fun isBlockedPath(path: String): Boolean {
        return BLOCKED_PATHS.any { path.startsWith(it) }
    }

    private val LOCALHOST_ADDRESSES = setOf(
        "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost",
    )
}
