package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/** Write raw text input to a session's PTY (as if typed by user). */
class WriteInputTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "write_input"
    override val description = "Write raw text to a terminal session's input (as if typed). Does not add newline."
    override val riskLevel = RiskLevel.MODERATE

    override val inputSchema = InputSchema(
        properties = mapOf(
            "text" to PropertySchema("string", "Text to write to the terminal"),
            "session" to PropertySchema("integer", "Session index (default: 0)"),
        ),
        required = listOf("text"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val text = arguments["text"] as? String
            ?: return ToolResult.Error("Missing required parameter: text")
        val session = (arguments["session"] as? Number)?.toInt() ?: 0

        bridge.writeToSession(session, text)
        return ToolResult.Success("Written ${text.length} chars to session $session")
    }
}
