package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/**
 * Get recent output from a specific terminal session.
 *
 * Similar to read_output but designed for the agent orchestration workflow:
 * session_index is required (agents must be explicit about which session
 * they are reading from when orchestrating multiple panes).
 */
class GetSessionOutputTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "get_session_output"
    override val description = "Get recent output from a specific terminal session by index."
    override val riskLevel = RiskLevel.SAFE

    override val inputSchema = InputSchema(
        properties = mapOf(
            "session_index" to PropertySchema("integer", "Session index to read from"),
            "lines" to PropertySchema("integer", "Number of lines to read (default: 50, max: 10000)"),
        ),
        required = listOf("session_index"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val sessionIndex = (arguments["session_index"] as? Number)?.toInt()
            ?: return ToolResult.Error("Missing required parameter: session_index")
        val lines = (arguments["lines"] as? Number)?.toInt()?.coerceIn(1, 10_000) ?: 50

        // Validate session exists
        val sessions = bridge.sessions.value
        if (sessions.none { it.index == sessionIndex }) {
            return ToolResult.Error("Session $sessionIndex not found. Use list_sessions to see active sessions.")
        }

        val output = bridge.readOutput(sessionIndex, lines)
        return if (output.isNotEmpty()) {
            ToolResult.Success(output)
        } else {
            ToolResult.Success("(no output)")
        }
    }
}
