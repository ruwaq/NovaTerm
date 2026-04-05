package com.novaterm.core.mcp.security

import com.novaterm.core.mcp.tool.McpTool

/**
 * Controls whether a tool invocation is allowed to proceed.
 *
 * Implementations:
 * - [AutoApprovalManager]: auto-approves SAFE tools, denies DANGEROUS
 * - Future: UI-based approval with Android notification + dialog
 */
interface ApprovalManager {

    /**
     * Request approval for a tool call.
     * May suspend if user interaction is needed (e.g., showing a dialog).
     *
     * @param tool The tool being invoked.
     * @param arguments The arguments passed to the tool.
     * @param clientAddress The remote address of the MCP client.
     * @return Whether the call is approved or denied.
     */
    suspend fun requestApproval(
        tool: McpTool,
        arguments: Map<String, Any?>,
        clientAddress: String,
    ): ApprovalResult
}

/** Result of an approval request. */
sealed interface ApprovalResult {
    data object Approved : ApprovalResult
    data class Denied(val reason: String) : ApprovalResult
}

/**
 * Auto-approval manager for local development.
 *
 * - SAFE tools: always approved from localhost
 * - MODERATE tools: approved from localhost
 * - DANGEROUS tools: DENIED — requires explicit user approval via UI dialog
 *   (not yet implemented; until then, dangerous tools are blocked)
 */
class AutoApprovalManager : ApprovalManager {

    override suspend fun requestApproval(
        tool: McpTool,
        arguments: Map<String, Any?>,
        clientAddress: String,
    ): ApprovalResult {
        // Block non-localhost connections
        if (!SecurityPolicy.isAllowedOrigin(clientAddress)) {
            return ApprovalResult.Denied("Remote connections not allowed")
        }

        return when (tool.riskLevel) {
            SecurityPolicy.RiskLevel.SAFE -> ApprovalResult.Approved
            SecurityPolicy.RiskLevel.MODERATE -> ApprovalResult.Approved
            SecurityPolicy.RiskLevel.DANGEROUS -> {
                // Dangerous tools (arbitrary command execution, file writes)
                // must be explicitly approved via UI dialog.
                // Until the approval dialog is implemented, deny by default.
                ApprovalResult.Denied(
                    "Tool '${tool.name}' has DANGEROUS risk level and requires explicit user approval"
                )
            }
        }
    }
}
