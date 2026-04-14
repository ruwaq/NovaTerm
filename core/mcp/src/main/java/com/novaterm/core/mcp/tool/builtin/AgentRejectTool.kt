package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/**
 * Reject (discard) all changes in an agent workspace.
 *
 * Runs `git checkout . && git clean -fd` in the workspace's working
 * directory. This is a DANGEROUS operation — requires approval
 * because it irreversibly discards uncommitted work.
 */
class AgentRejectTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "agent_reject"
    override val description = "Reject and discard all uncommitted changes in an agent workspace. Runs git checkout . && git clean -fd. This action cannot be undone."
    override val riskLevel = RiskLevel.DANGEROUS

    override val inputSchema = InputSchema(
        properties = mapOf(
            "workspace_id" to PropertySchema("string", "The workspace ID to reject changes for"),
        ),
        required = listOf("workspace_id"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val workspaceId = arguments["workspace_id"] as? String
            ?: return ToolResult.Error("Missing required parameter: workspace_id")

        bridge.getWorkspace(workspaceId)
            ?: return ToolResult.Error("Workspace not found: $workspaceId")

        val success = bridge.rejectAgentChanges(workspaceId)
        return if (success) {
            ToolResult.Success("Changes rejected and discarded in workspace $workspaceId.")
        } else {
            ToolResult.Error("Failed to discard changes in workspace $workspaceId.")
        }
    }
}