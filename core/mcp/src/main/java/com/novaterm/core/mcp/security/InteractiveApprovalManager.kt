package com.novaterm.core.mcp.security

import com.novaterm.core.mcp.tool.McpTool
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A pending approval request displayed to the user via UI dialog.
 */
data class ApprovalRequest(
    val id: Long,
    val toolName: String,
    val riskLevel: SecurityPolicy.RiskLevel,
    val arguments: Map<String, Any?>,
    val clientAddress: String,
    internal val deferred: CompletableDeferred<Boolean>,
) {
    /** Approve the request — the tool call will proceed. */
    fun approve() { deferred.complete(true) }

    /** Deny the request — the tool call will be rejected. */
    fun deny() { deferred.complete(false) }
}

/**
 * Interactive approval manager that shows DANGEROUS tool calls to the user.
 *
 * - SAFE/MODERATE from localhost: auto-approved (same as [AutoApprovalManager])
 * - DANGEROUS from localhost: suspended until user approves/denies via UI dialog
 * - Non-localhost: always denied
 *
 * The UI layer observes [pendingRequest] and shows an approval dialog.
 * The dialog calls [ApprovalRequest.approve] or [ApprovalRequest.deny].
 */
class InteractiveApprovalManager : ApprovalManager {

    private val _pendingRequest = MutableStateFlow<ApprovalRequest?>(null)

    /** Observable pending approval request. UI should show a dialog when non-null. */
    val pendingRequest: StateFlow<ApprovalRequest?> = _pendingRequest.asStateFlow()

    private val nextId = AtomicLong(0)

    override suspend fun requestApproval(
        tool: McpTool,
        arguments: Map<String, Any?>,
        clientAddress: String,
    ): ApprovalResult {
        if (!SecurityPolicy.isAllowedOrigin(clientAddress)) {
            return ApprovalResult.Denied("Remote connections not allowed")
        }

        return when (tool.riskLevel) {
            SecurityPolicy.RiskLevel.SAFE -> ApprovalResult.Approved
            SecurityPolicy.RiskLevel.MODERATE -> ApprovalResult.Approved
            SecurityPolicy.RiskLevel.DANGEROUS -> requestUserApproval(tool, arguments, clientAddress)
        }
    }

    private suspend fun requestUserApproval(
        tool: McpTool,
        arguments: Map<String, Any?>,
        clientAddress: String,
    ): ApprovalResult {
        val deferred = CompletableDeferred<Boolean>()
        val request = ApprovalRequest(
            id = nextId.getAndIncrement(),
            toolName = tool.name,
            riskLevel = tool.riskLevel,
            arguments = arguments,
            clientAddress = clientAddress,
            deferred = deferred,
        )

        _pendingRequest.value = request

        // Wait up to 60s for user response, then deny by timeout
        val approved = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) {
            deferred.await()
        } ?: false

        _pendingRequest.value = null
        return if (approved) {
            ApprovalResult.Approved
        } else {
            ApprovalResult.Denied("User denied tool '${tool.name}'")
        }
    }

    companion object {
        private const val APPROVAL_TIMEOUT_MS = 60_000L
    }
}
