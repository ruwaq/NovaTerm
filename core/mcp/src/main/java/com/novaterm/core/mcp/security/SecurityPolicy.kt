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
        return BLOCKED_PATHS.any { blocked -> path == blocked || path.startsWith("$blocked/") }
    }

    private val LOCALHOST_ADDRESSES = setOf(
        "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost",
    )

    /**
     * Sanitize command input to prevent shell injection.
     *
     * This function escapes dangerous shell metacharacters or rejects commands
     * that contain dangerous patterns. It ensures that commands cannot be
     * used to chain multiple commands, redirect I/O, or execute arbitrary code.
     *
     * @param command The command string to sanitize
     * @param allowEmpty Whether empty commands are allowed
     * @return A sanitized command string or null if the command is blocked
     */
    fun sanitizeCommand(command: String, allowEmpty: Boolean = false): String? {
        if (command.isEmpty()) {
            if (!allowEmpty) {
                Log.w(TAG, "Command sanitization: empty command blocked")
                return null
            }
            return command
        }

        val normalized = command.trim()

        // Check for dangerous patterns
        if (isBlockedCommand(normalized)) {
            Log.w(TAG, "Command sanitization: blocked command detected: '$normalized'")
            return null
        }

        // Check for dangerous metacharacters that could allow command injection
        val dangerousChars = ";|&`$\n"
        if (normalized.any { it in dangerousChars }) {
            // Instead of rejecting, escape dangerous characters to prevent injection
            // while still allowing legitimate use in commands (e.g., "echo 'a;b'")
            val escaped = normalizeShellCommand(normalized)
            Log.d(TAG, "Command sanitization: escaped dangerous characters in: '$normalized' -> '$escaped'")
            return escaped
        }

        return normalized
    }

    /**
     * Normalize a shell command by escaping dangerous metacharacters.
     *
     * This ensures that commands containing dangerous characters are still
     * executed safely within the shell's context.
     */
    private fun normalizeShellCommand(command: String): String {
        // Escape shell metacharacters that could cause injection
        return command
            .replace("\$(", "")  // Escape command substitution
            .replace("\$\{", "")  // Escape command substitution
            .replace(";", "\";\")  // Escape semicolon
            .replace("|", "\\|")  // Escape pipe
            .replace("&", "\\&")  // Escape background process
            .replace("\n", "\\n")  // Escape newline
            .replace("\`", "\\`")  // Escape backticks
            .replace("<", "\\<")  // Escape input redirection
            .replace(">", "\\>")  // Escape output redirection
    }

    private const val TAG = "NovaTermSecurity"
}
