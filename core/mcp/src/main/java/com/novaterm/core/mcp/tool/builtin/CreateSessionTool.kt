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
 * Create a new terminal session.
 *
 * Enables AI agents to spawn additional terminal panes for parallel
 * task orchestration (e.g., run tests in one session while editing in another).
 * Optionally runs an initial command in the new session.
 */
class CreateSessionTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "create_session"
    override val description = "Create a new terminal session. Optionally run an initial command in it."
    override val riskLevel = RiskLevel.MODERATE

    override val inputSchema = InputSchema(
        properties = mapOf(
            "command" to PropertySchema("string", "Optional command to run in the new session"),
        ),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val command = arguments["command"] as? String

        // Security: check blocked commands if a command was provided
        if (command != null && SecurityPolicy.isBlockedCommand(command)) {
            return ToolResult.Error("Command blocked by security policy: $command")
        }

        val index = bridge.createSession()
        if (index < 0) {
            return ToolResult.Error("Failed to create session")
        }

        // If a command was provided, write it to the new session after a brief delay
        // to allow the shell to initialize
        if (command != null) {
            delay(500)
            bridge.writeToSession(index, "$command\n")
        }

        return ToolResult.Success("Created session $index")
    }
}
