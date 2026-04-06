package com.novaterm.core.mcp.security

import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.ToolResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveApprovalManagerTest {

    private fun toolWithRisk(name: String = "test", risk: RiskLevel) = object : McpTool {
        override val name = name
        override val description = "test"
        override val inputSchema = InputSchema(emptyMap())
        override val riskLevel = risk
        override suspend fun execute(arguments: Map<String, Any?>) = ToolResult.Success("ok")
    }

    @Test
    fun `safe tool from localhost is auto-approved`() = runTest {
        val mgr = InteractiveApprovalManager()
        val result = mgr.requestApproval(toolWithRisk(risk = RiskLevel.SAFE), emptyMap(), "127.0.0.1")
        assertTrue(result is ApprovalResult.Approved)
        assertNull(mgr.pendingRequest.value)
    }

    @Test
    fun `moderate tool from localhost is auto-approved`() = runTest {
        val mgr = InteractiveApprovalManager()
        val result = mgr.requestApproval(toolWithRisk(risk = RiskLevel.MODERATE), emptyMap(), "127.0.0.1")
        assertTrue(result is ApprovalResult.Approved)
    }

    @Test
    fun `dangerous tool from remote IP is denied without dialog`() = runTest {
        val mgr = InteractiveApprovalManager()
        val result = mgr.requestApproval(toolWithRisk(risk = RiskLevel.DANGEROUS), emptyMap(), "10.0.0.5")
        assertTrue(result is ApprovalResult.Denied)
        assertNull(mgr.pendingRequest.value)
    }

    @Test
    fun `dangerous tool approved by user returns Approved`() = runTest {
        val mgr = InteractiveApprovalManager()
        val deferred = async {
            mgr.requestApproval(
                toolWithRisk(name = "run_command", risk = RiskLevel.DANGEROUS),
                mapOf("command" to "ls"),
                "127.0.0.1",
            )
        }
        // Give the async coroutine time to suspend on the deferred
        delay(100)
        val request = mgr.pendingRequest.value
        assertNotNull("Expected pending request", request)
        assertEquals("run_command", request!!.toolName)
        assertEquals(RiskLevel.DANGEROUS, request.riskLevel)

        request.approve()
        val result = deferred.await()
        assertTrue(result is ApprovalResult.Approved)
        assertNull(mgr.pendingRequest.value)
    }

    @Test
    fun `dangerous tool denied by user returns Denied`() = runTest {
        val mgr = InteractiveApprovalManager()
        val deferred = async {
            mgr.requestApproval(
                toolWithRisk(name = "file_write", risk = RiskLevel.DANGEROUS),
                mapOf("path" to "/tmp/x"),
                "127.0.0.1",
            )
        }
        delay(100)
        val request = mgr.pendingRequest.value
        assertNotNull("Expected pending request", request)
        request!!.deny()
        val result = deferred.await()
        assertTrue(result is ApprovalResult.Denied)
        assertTrue((result as ApprovalResult.Denied).reason.contains("file_write"))
    }

    @Test
    fun `approval request IDs are unique`() = runTest {
        val mgr = InteractiveApprovalManager()
        val ids = mutableSetOf<Long>()
        repeat(3) {
            val deferred = async {
                mgr.requestApproval(
                    toolWithRisk(risk = RiskLevel.DANGEROUS),
                    emptyMap(),
                    "127.0.0.1",
                )
            }
            delay(100)
            val request = mgr.pendingRequest.value!!
            ids.add(request.id)
            request.approve()
            deferred.await()
        }
        assertEquals("IDs should be unique", 3, ids.size)
    }

    @Test
    fun `pendingRequest shows arguments`() = runTest {
        val mgr = InteractiveApprovalManager()
        val args = mapOf<String, Any?>("command" to "rm -rf /tmp/test", "session" to 0)
        val deferred = async {
            mgr.requestApproval(
                toolWithRisk(name = "run_command", risk = RiskLevel.DANGEROUS),
                args,
                "127.0.0.1",
            )
        }
        delay(100)
        val request = mgr.pendingRequest.value!!
        assertEquals("rm -rf /tmp/test", request.arguments["command"])
        assertEquals(0, request.arguments["session"])
        request.deny()
        deferred.await()
    }
}
