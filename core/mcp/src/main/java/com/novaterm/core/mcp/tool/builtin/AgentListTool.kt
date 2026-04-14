package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.ToolResult

/**
 * List all AI agent workspaces with their status, type, and runtime.
 *
 * Enables remote monitoring of parallel agent execution from an MCP client
 * (e.g., Claude Code checking what agents are running on the phone).
 */
class AgentListTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "agent_list"
    override val description = "List all AI agent workspaces with their ID, name, type, status, runtime, and working directory."
    override val riskLevel = RiskLevel.SAFE
    override val inputSchema = InputSchema(properties = emptyMap())

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val workspaces = bridge.listWorkspaces()
        if (workspaces.isEmpty()) {
            return ToolResult.Success("No agent workspaces.")
        }
        val text = workspaces.joinToString("\n") { w ->
            "[${w.id}] ${w.name} (${w.agentType}) [${w.status}] pid=${w.pid} runtime=${w.runtimeSeconds}s dir=${w.workingDir}"
        }
        return ToolResult.Success(text)
    }
}