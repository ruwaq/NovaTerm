package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wait until a pattern appears in a session's output or timeout.
 *
 * This is the most important tool for AI agent orchestration. It lets
 * agents run a command and wait for it to finish:
 *
 * ```
 * run_command({ session: 0, command: "npm test" })
 * wait_for_output({ session_index: 0, pattern: "Tests:", timeout_ms: 60000 })
 * ```
 *
 * Uses a polling approach: checks the last N lines of the terminal buffer
 * every 500ms. Returns immediately when the pattern is found, or returns
 * matched=false on timeout.
 *
 * The pattern is a simple string contains check (not regex) for safety.
 */
class WaitForOutputTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "wait_for_output"
    override val description = "Wait until a pattern appears in a session's output or timeout. Pattern is a simple string match (not regex)."
    override val riskLevel = RiskLevel.SAFE

    override val inputSchema = InputSchema(
        properties = mapOf(
            "session_index" to PropertySchema("integer", "Session index to monitor"),
            "pattern" to PropertySchema("string", "Text pattern to wait for (simple string contains, not regex)"),
            "timeout_ms" to PropertySchema("integer", "Timeout in milliseconds (default: 30000, max: 120000)"),
        ),
        required = listOf("session_index", "pattern"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val sessionIndex = (arguments["session_index"] as? Number)?.toInt()
            ?: return ToolResult.Error("Missing required parameter: session_index")
        val pattern = arguments["pattern"] as? String
            ?: return ToolResult.Error("Missing required parameter: pattern")
        val timeoutMs = (arguments["timeout_ms"] as? Number)?.toLong()
            ?.coerceIn(500, MAX_TIMEOUT_MS) ?: DEFAULT_TIMEOUT_MS

        // Validate session exists
        val sessions = bridge.sessions.value
        if (sessions.none { it.index == sessionIndex }) {
            return ToolResult.Error("Session $sessionIndex not found. Use list_sessions to see active sessions.")
        }

        // Poll the terminal buffer until pattern appears or timeout
        val matchedOutput = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val output = bridge.readOutput(sessionIndex, POLL_LINES)
                if (output.contains(pattern)) {
                    return@withTimeoutOrNull output
                }
                delay(POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            null // unreachable but satisfies type checker
        }

        return if (matchedOutput != null) {
            ToolResult.Success("{\"matched\":true,\"output\":${escapeJson(matchedOutput)}}")
        } else {
            // Return the latest output even on timeout so the agent has context
            val lastOutput = bridge.readOutput(sessionIndex, POLL_LINES)
            ToolResult.Success("{\"matched\":false,\"output\":${escapeJson(lastOutput)}}")
        }
    }

    /** Escape a string for embedding in JSON. */
    private fun escapeJson(value: String): String {
        val sb = StringBuilder("\"")
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u%04x".format(ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
        private const val POLL_LINES = 100
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_TIMEOUT_MS = 120_000L
    }
}
