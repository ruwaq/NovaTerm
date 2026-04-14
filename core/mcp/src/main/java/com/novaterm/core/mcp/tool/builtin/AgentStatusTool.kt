package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/**
 * Get the status of a specific agent workspace.
 *
 * Returns detailed info: name, type, status, PID, runtime, session, working dir.
 */
class AgentStatusTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "agent_status"
    override val description = "Get the detailed status of a specific agent workspace by ID."
    override val riskLevel = RiskLevel.SAFE

    override val inputSchema = InputSchema(
        properties = mapOf(
            "workspace_id" to PropertySchema("string", "The workspace ID to query"),
        ),
        required = listOf("workspace_id"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val workspaceId = arguments["workspace_id"] as? String
            ?: return ToolResult.Error("Missing required parameter: workspace_id")

        val workspace = bridge.getWorkspace(workspaceId)
            ?: return ToolResult.Error("Workspace not found: $workspaceId")

        val text = buildString {
            appendLine("ID: ${workspace.id}")
            appendLine("Name: ${workspace.name}")
            appendLine("Type: ${workspace.agentType}")
            appendLine("Status: ${workspace.status}")
            appendLine("PID: ${workspace.pid}")
            appendLine("Session: ${workspace.sessionId}")
            appendLine("Runtime: ${workspace.runtimeSeconds}s")
            appendLine("Working dir: ${workspace.workingDir}")
            appendLine("Last output: ${workspace.lastOutputAt}")
        }
        return ToolResult.Success(text.trimEnd())
    }
}