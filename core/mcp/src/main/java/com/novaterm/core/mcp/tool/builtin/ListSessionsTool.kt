package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.ToolResult

/** List all active terminal sessions with their IDs, titles, and status. */
class ListSessionsTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "list_sessions"
    override val description = "List all active terminal sessions with their index, title, CWD, and status."
    override val riskLevel = RiskLevel.SAFE
    override val inputSchema = InputSchema(properties = emptyMap())

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val sessions = bridge.sessions.value
        if (sessions.isEmpty()) {
            return ToolResult.Success("No active sessions.")
        }
        val text = sessions.joinToString("\n") { s ->
            val status = if (s.isRunning) "running" else "stopped"
            "[${s.index}] ${s.title} (${s.cwd}) [$status] pid=${s.pid}"
        }
        return ToolResult.Success(text)
    }
}
