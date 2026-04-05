package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult
import kotlinx.coroutines.delay

/**
 * Execute a shell command in a terminal session.
 *
 * Writes the command to the session's PTY, waits for output,
 * and returns the result. This is the most powerful tool —
 * it enables AI agents to interact with the terminal as a user would.
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
            "wait_ms" to PropertySchema("integer", "Time to wait for output in ms (default: 3000, max: 30000)"),
        ),
        required = listOf("command"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val command = arguments["command"] as? String
            ?: return ToolResult.Error("Missing required parameter: command")
        val session = (arguments["session"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val waitMs = (arguments["wait_ms"] as? Number)?.toLong()?.coerceIn(500, 30_000) ?: 3_000

        // Security: check blocked commands
        if (SecurityPolicy.isBlockedCommand(command)) {
            return ToolResult.Error("Command blocked by security policy: $command")
        }

        // Write command to PTY
        bridge.writeToSession(session, "$command\n")

        // Wait for output to appear
        delay(waitMs)

        // Read recent output
        val output = bridge.readOutput(session, 100)
        return ToolResult.Success(output)
    }
}
