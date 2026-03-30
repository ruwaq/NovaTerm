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

    /** Patterns that are never allowed via MCP, regardless of approval. */
    private val BLOCKED_PATTERNS = listOf(
        Regex("""^rm\s+-rf\s+/\s*$"""),       // rm -rf /
        Regex("""^rm\s+-rf\s+/\*"""),          // rm -rf /*
        Regex("""^mkfs"""),                     // mkfs anything
        Regex("""^dd\s+if=/dev/"""),            // dd from device
        Regex(""":\(\)\s*\{.*\|.*&\s*\}"""),   // fork bomb variants
        Regex("""^chmod\s+-R\s+777\s+/\s*$"""), // chmod 777 /
    )

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

    /** Check if a command is blocked by policy. */
    fun isBlockedCommand(command: String): Boolean {
        val normalized = command.trim()
        return BLOCKED_PATTERNS.any { it.containsMatchIn(normalized) }
    }

    /** Check if a file path is blocked by policy. */
    fun isBlockedPath(path: String): Boolean {
        return BLOCKED_PATHS.any { path.startsWith(it) }
    }

    private val LOCALHOST_ADDRESSES = setOf(
        "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost",
    )
}
