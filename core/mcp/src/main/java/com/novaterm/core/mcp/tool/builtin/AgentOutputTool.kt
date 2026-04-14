package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/**
 * Read recent output from an agent workspace's terminal session.
 *
 * Enables remote inspection of what an agent is currently doing
 * without switching to the phone UI.
 */
class AgentOutputTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "agent_output"
    override val description = "Read recent output from an agent workspace's terminal session."
    override val riskLevel = RiskLevel.SAFE

    override val inputSchema = InputSchema(
        properties = mapOf(
            "workspace_id" to PropertySchema("string", "The workspace ID to read output from"),
            "lines" to PropertySchema("integer", "Number of recent lines to read (default: 50, max: 500)", default = 50),
        ),
        required = listOf("workspace_id"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val workspaceId = arguments["workspace_id"] as? String
            ?: return ToolResult.Error("Missing required parameter: workspace_id")

        val lines = (arguments["lines"] as? Number)?.toInt()?.coerceIn(1, 500) ?: 50

        // Verify workspace exists
        bridge.getWorkspace(workspaceId)
            ?: return ToolResult.Error("Workspace not found: $workspaceId")

        val output = bridge.getAgentOutput(workspaceId, lines)
        if (output.isBlank()) {
            return ToolResult.Success("No output available (workspace may not have an active session).")
        }
        return ToolResult.Success(output)
    }
}