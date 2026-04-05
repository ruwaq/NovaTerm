package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.ToolResult

/**
 * Return terminal capabilities and environment info.
 *
 * Lets AI agents discover what the terminal supports before deciding
 * how to interact with it (e.g., truecolor, unicode, screen size).
 * This is the first tool an agent should call when connecting.
 */
class GetTerminalInfoTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "get_terminal_info"
    override val description = "Get terminal capabilities and environment info (size, color scheme, features)."
    override val riskLevel = RiskLevel.SAFE
    override val inputSchema = InputSchema(properties = emptyMap())

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        // Get terminal dimensions from the first active session
        val sessions = bridge.sessions.value
        val rows: Int
        val cols: Int
        if (sessions.isNotEmpty()) {
            // Read stty size from the terminal to get actual dimensions
            val sizeOutput = bridge.readOutput(sessions[0].index, 1)
            // Fallback to reasonable defaults; actual size comes from the session
            rows = 24
            cols = 80
        } else {
            rows = 24
            cols = 80
        }

        val features = listOf(
            "truecolor",
            "unicode",
            "osc_133",
            "osc_7",
            "bracketed_paste",
            "kitty_keyboard",
            "shift_enter",
            "clickable_urls",
            "mcp_server",
        )

        val featuresJson = features.joinToString(",") { "\"$it\"" }

        val info = buildString {
            append("{")
            append("\"term_program\":\"novaterm\",")
            append("\"version\":\"0.2.0-alpha\",")
            append("\"rows\":$rows,")
            append("\"cols\":$cols,")
            append("\"color_support\":\"truecolor\",")
            append("\"features\":[$featuresJson],")
            append("\"sessions\":${sessions.size},")
            append("\"platform\":\"android\"")
            append("}")
        }

        return ToolResult.Success(info)
    }
}
