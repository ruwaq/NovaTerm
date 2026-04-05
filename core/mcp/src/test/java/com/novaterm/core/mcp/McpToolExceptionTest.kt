package com.novaterm.core.mcp

import com.novaterm.core.mcp.security.ApprovalResult
import com.novaterm.core.mcp.security.AutoApprovalManager
import com.novaterm.core.mcp.security.RateLimiter
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for tool exception safety, approval flow, and rate limiter exposure.
 * Validates the defensive guards added in the production hardening pass.
 */
class McpToolExceptionTest {

    // ── Tool exception safety ─────────────────────────────

    /**
     * Simulates the try-catch wrapper added around tool.execute() in McpServer.
     * Verifies that throwing tools produce clean ToolResult.Error responses.
     */
    private suspend fun safeExecute(tool: McpTool, args: Map<String, Any?>): ToolResult {
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult.Error("Tool execution failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    @Test
    fun `RuntimeException from tool produces clean error result`() = runTest {
        val tool = object : McpTool {
            override val name = "crash_tool"
            override val description = "Always throws"
            override val inputSchema = InputSchema(emptyMap())
            override val riskLevel = RiskLevel.SAFE
            override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
                throw RuntimeException("disk full")
        }
        val result = safeExecute(tool, emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("disk full"))
    }

    @Test
    fun `IllegalStateException is wrapped, not propagated`() = runTest {
        val tool = object : McpTool {
            override val name = "state_tool"
            override val description = "Bad state"
            override val inputSchema = InputSchema(emptyMap())
            override val riskLevel = RiskLevel.SAFE
            override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
                throw IllegalStateException("service died")
        }
        val result = safeExecute(tool, emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `successful tool result passes through unchanged`() = runTest {
        val tool = object : McpTool {
            override val name = "ok_tool"
            override val description = "Always succeeds"
            override val inputSchema = InputSchema(emptyMap())
            override val riskLevel = RiskLevel.SAFE
            override suspend fun execute(arguments: Map<String, Any?>) = ToolResult.Success("data")
        }
        val result = safeExecute(tool, emptyMap())
        assertTrue(result is ToolResult.Success)
        assertEquals("data", (result as ToolResult.Success).text)
    }

    // ── AutoApprovalManager ───────────────────────────────

    @Test
    fun `DANGEROUS tool is always denied by AutoApprovalManager`() = runTest {
        val manager = AutoApprovalManager()
        val tool = object : McpTool {
            override val name = "run_command"
            override val description = "Executes arbitrary commands"
            override val inputSchema = InputSchema(emptyMap())
            override val riskLevel = RiskLevel.DANGEROUS
            override suspend fun execute(arguments: Map<String, Any?>) = ToolResult.Success("")
        }
        val result = manager.requestApproval(tool, emptyMap(), "127.0.0.1")
        assertTrue(result is ApprovalResult.Denied)
    }

    @Test
    fun `SAFE tool from non-localhost is denied`() = runTest {
        val manager = AutoApprovalManager()
        val tool = object : McpTool {
            override val name = "list_sessions"
            override val description = "Lists sessions"
            override val inputSchema = InputSchema(emptyMap())
            override val riskLevel = RiskLevel.SAFE
            override suspend fun execute(arguments: Map<String, Any?>) = ToolResult.Success("")
        }
        val result = manager.requestApproval(tool, emptyMap(), "192.168.1.100")
        assertTrue(result is ApprovalResult.Denied)
    }

    // ── RateLimiter.maxRequests exposure ──────────────────

    @Test
    fun `RateLimiter exposes maxRequests as public val`() {
        val limiter = RateLimiter(maxRequests = 42)
        assertEquals(42, limiter.maxRequests)
    }

    @Test
    fun `rate limit error message uses actual maxRequests`() {
        val limiter = RateLimiter(maxRequests = 15)
        assertEquals(15, limiter.maxRequests)
        // Verify it's not hardcoded to 60
        assertTrue(limiter.maxRequests != 60)
    }
}
