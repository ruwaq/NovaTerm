package com.novaterm.core.mcp.security

import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalManagerTest {

    private fun toolWithRisk(risk: RiskLevel) = object : McpTool {
        override val name = "test"
        override val description = "test"
        override val inputSchema = InputSchema(emptyMap())
        override val riskLevel = risk
        override suspend fun execute(arguments: Map<String, Any?>) = ToolResult.Success("ok")
    }

    @Test
    fun `safe tool from localhost is approved`() = runTest {
        val manager = AutoApprovalManager()
        val result = manager.requestApproval(toolWithRisk(RiskLevel.SAFE), emptyMap(), "127.0.0.1")
        assertTrue(result is ApprovalResult.Approved)
    }

    @Test
    fun `moderate tool from localhost is approved`() = runTest {
        val manager = AutoApprovalManager()
        val result = manager.requestApproval(toolWithRisk(RiskLevel.MODERATE), emptyMap(), "127.0.0.1")
        assertTrue(result is ApprovalResult.Approved)
    }

    @Test
    fun `dangerous tool from localhost is denied`() = runTest {
        val manager = AutoApprovalManager()
        val result = manager.requestApproval(toolWithRisk(RiskLevel.DANGEROUS), emptyMap(), "127.0.0.1")
        assertTrue(result is ApprovalResult.Denied)
    }

    @Test
    fun `any tool from remote IP is denied`() = runTest {
        val manager = AutoApprovalManager()
        val result = manager.requestApproval(toolWithRisk(RiskLevel.SAFE), emptyMap(), "192.168.1.100")
        assertTrue(result is ApprovalResult.Denied)
    }

    @Test
    fun `any tool from public IP is denied`() = runTest {
        val manager = AutoApprovalManager()
        val result = manager.requestApproval(toolWithRisk(RiskLevel.SAFE), emptyMap(), "8.8.8.8")
        assertTrue(result is ApprovalResult.Denied)
    }

    @Test
    fun `IPv6 localhost safe tool is approved`() = runTest {
        val manager = AutoApprovalManager()
        val result = manager.requestApproval(toolWithRisk(RiskLevel.SAFE), emptyMap(), "::1")
        assertTrue(result is ApprovalResult.Approved)
    }

    @Test
    fun `IPv6 localhost dangerous tool is denied`() = runTest {
        val manager = AutoApprovalManager()
        val result = manager.requestApproval(toolWithRisk(RiskLevel.DANGEROUS), emptyMap(), "::1")
        assertTrue(result is ApprovalResult.Denied)
    }
}
