package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/**
 * Get the git diff of uncommitted changes in an agent workspace.
 *
 * Returns the raw unified diff output that can be parsed and reviewed
 * remotely. Used to review agent work before approving or rejecting.
 */
class AgentDiffTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "agent_diff"
    override val description = "Get the git diff of uncommitted changes in an agent workspace. Returns unified diff format."
    override val riskLevel = RiskLevel.SAFE

    override val inputSchema = InputSchema(
        properties = mapOf(
            "workspace_id" to PropertySchema("string", "The workspace ID to get diff for"),
        ),
        required = listOf("workspace_id"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val workspaceId = arguments["workspace_id"] as? String
            ?: return ToolResult.Error("Missing required parameter: workspace_id")

        bridge.getWorkspace(workspaceId)
            ?: return ToolResult.Error("Workspace not found: $workspaceId")

        val diff = bridge.runAgentDiff(workspaceId)
        if (diff.isBlank()) {
            return ToolResult.Success("No uncommitted changes in workspace $workspaceId.")
        }
        return ToolResult.Success(diff)
    }
}