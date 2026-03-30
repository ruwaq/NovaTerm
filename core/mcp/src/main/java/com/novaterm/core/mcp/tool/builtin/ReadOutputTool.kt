package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/** Read recent output from a terminal session's scrollback buffer. */
class ReadOutputTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "read_output"
    override val description = "Read the last N lines of output from a terminal session."
    override val riskLevel = RiskLevel.SAFE
    override val inputSchema = InputSchema(
        properties = mapOf(
            "session" to PropertySchema("integer", "Session index (default: 0)"),
            "lines" to PropertySchema("integer", "Number of lines to read (default: 50, max: 10000)"),
        ),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val session = (arguments["session"] as? Number)?.toInt() ?: 0
        val lines = (arguments["lines"] as? Number)?.toInt()?.coerceIn(1, 10_000) ?: 50
        val output = bridge.readOutput(session, lines)
        return if (output.isNotEmpty()) {
            ToolResult.Success(output)
        } else {
            ToolResult.Success("(no output)")
        }
    }
}
