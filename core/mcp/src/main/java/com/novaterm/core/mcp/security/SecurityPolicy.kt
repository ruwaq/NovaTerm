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

    /** Commands that are never allowed via MCP, regardless of approval. */
    val BLOCKED_COMMANDS = setOf(
        "rm -rf /",
        "rm -rf /*",
        "mkfs",
        "dd if=/dev",
        ":(){:|:&};:",  // fork bomb
        "chmod -R 777 /",
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
        val normalized = command.trim().lowercase()
        return BLOCKED_COMMANDS.any { normalized.startsWith(it) }
    }

    /** Check if a file path is blocked by policy. */
    fun isBlockedPath(path: String): Boolean {
        return BLOCKED_PATHS.any { path.startsWith(it) }
    }

    private val LOCALHOST_ADDRESSES = setOf(
        "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost",
    )
}
