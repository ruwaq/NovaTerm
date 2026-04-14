package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/**
 * Approve (commit) all changes in an agent workspace.
 *
 * Runs `git add -A && git commit -m "<message>"` in the workspace's
 * working directory. This is a DANGEROUS operation — requires approval
 * because it makes persistent changes to the repository.
 */
class AgentApproveTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "agent_approve"
    override val description = "Approve and commit all changes in an agent workspace. Runs git add -A && git commit."
    override val riskLevel = RiskLevel.DANGEROUS

    override val inputSchema = InputSchema(
        properties = mapOf(
            "workspace_id" to PropertySchema("string", "The workspace ID to approve changes for"),
            "message" to PropertySchema("string", "Optional commit message (default: auto-generated from workspace name)"),
        ),
        required = listOf("workspace_id"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val workspaceId = arguments["workspace_id"] as? String
            ?: return ToolResult.Error("Missing required parameter: workspace_id")

        val message = (arguments["message"] as? String).orEmpty()

        bridge.getWorkspace(workspaceId)
            ?: return ToolResult.Error("Workspace not found: $workspaceId")

        val success = bridge.approveAgentChanges(workspaceId, message)
        return if (success) {
            ToolResult.Success("Changes approved and committed in workspace $workspaceId.")
        } else {
            ToolResult.Error("Failed to commit changes in workspace $workspaceId. There may be no changes to commit or git is not initialized.")
        }
    }
}