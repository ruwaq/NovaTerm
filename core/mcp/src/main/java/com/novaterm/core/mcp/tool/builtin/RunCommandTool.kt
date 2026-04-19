package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Execute a shell command in a terminal session.
 *
 * Writes the command to the session's PTY, then polls for output
 * stabilization instead of sleeping a fixed duration. Returns as soon
 * as output has been unchanged for two consecutive reads (≈600ms), or
 * after [wait_ms] milliseconds at the latest.
 *
 * Example interaction:
 * ```
 * run_command({ command: "ls" })       → returns in ~600ms
 * run_command({ command: "npm test" }) → returns up to wait_ms
 * ```
 */
class RunCommandTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "run_command"
    override val description = "Execute a shell command in a terminal session and return the output."
    override val riskLevel = RiskLevel.DANGEROUS

    override val inputSchema = InputSchema(
        properties = mapOf(
            "command" to PropertySchema("string", "The shell command to execute"),
            "session" to PropertySchema("integer", "Session index (default: 0)"),
            "wait_ms" to PropertySchema("integer", "Max time to wait for output in ms (default: 10000, max: 30000)"),
        ),
        required = listOf("command"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val command = arguments["command"] as? String
            ?: return ToolResult.Error("Missing required parameter: command")

        if (command.length > MAX_COMMAND_LENGTH) {
            return ToolResult.Error("Command too long (${command.length} chars, max $MAX_COMMAND_LENGTH)")
        }

        val session = (arguments["session"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val waitMs = (arguments["wait_ms"] as? Number)?.toLong()?.coerceIn(500, 30_000) ?: DEFAULT_WAIT_MS

        // Block dangerous commands per security policy
        if (SecurityPolicy.isBlockedCommand(command)) {
            return ToolResult.Error("Command blocked by security policy")
        }

        // Write command to PTY
        bridge.writeToSession(session, "$command\n")

        // Poll for output stabilization: stop when output is non-empty and
        // unchanged for STABLE_READS_REQUIRED consecutive reads. Falls back to
        // returning whatever output is available when the timeout expires.
        val stableOutput = withTimeoutOrNull(waitMs) {
            var lastOutput = ""
            var stableCount = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val current = bridge.readOutput(session, MAX_OUTPUT_LINES)
                if (current.isNotEmpty() && current == lastOutput) {
                    stableCount++
                    if (stableCount >= STABLE_READS_REQUIRED) {
                        return@withTimeoutOrNull current
                    }
                } else {
                    stableCount = 0
                    lastOutput = current
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

        val output = stableOutput ?: bridge.readOutput(session, MAX_OUTPUT_LINES)
        return ToolResult.Success(output)
    }

    companion object {
        /** Polling interval between output reads. */
        private const val POLL_INTERVAL_MS = 300L
        /** Number of consecutive identical reads before declaring output stable. */
        private const val STABLE_READS_REQUIRED = 2
        /** Default timeout: 10s (was 3s fixed delay — faster for fast commands). */
        private const val DEFAULT_WAIT_MS = 10_000L
        private const val MAX_OUTPUT_LINES = 100
        private const val MAX_COMMAND_LENGTH = 4096
    }
}
