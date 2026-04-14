package com.novaterm.core.mcp.tool

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.bridge.McpWorkspaceInfo
import com.novaterm.core.mcp.tool.builtin.AgentListTool
import com.novaterm.core.mcp.tool.builtin.AgentStatusTool
import com.novaterm.core.mcp.tool.builtin.AgentOutputTool
import com.novaterm.core.mcp.tool.builtin.AgentDiffTool
import com.novaterm.core.mcp.tool.builtin.AgentApproveTool
import com.novaterm.core.mcp.tool.builtin.AgentRejectTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the 6 MCP agent orchestration tools.
 * Uses a fake McpSessionBridge for isolated testing.
 */
class AgentToolsTest {

    private lateinit var fakeBridge: FakeMcpBridge
    private lateinit var agentList: AgentListTool
    private lateinit var agentStatus: AgentStatusTool
    private lateinit var agentOutput: AgentOutputTool
    private lateinit var agentDiff: AgentDiffTool
    private lateinit var agentApprove: AgentApproveTool
    private lateinit var agentReject: AgentRejectTool

    @Before
    fun setup() {
        fakeBridge = FakeMcpBridge()
        agentList = AgentListTool(fakeBridge)
        agentStatus = AgentStatusTool(fakeBridge)
        agentOutput = AgentOutputTool(fakeBridge)
        agentDiff = AgentDiffTool(fakeBridge)
        agentApprove = AgentApproveTool(fakeBridge)
        agentReject = AgentRejectTool(fakeBridge)
    }

    // ── AgentListTool ─────────────────────────────────────────────

    @Test
    fun `agent_list with no workspaces`() = kotlinx.coroutines.test.runTest {
        val result = agentList.execute(emptyMap())
        assertTrue((result as ToolResult.Success).text.contains("No agent workspaces"))
    }

    @Test
    fun `agent_list lists all workspaces`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude Code", "Claude Code", "running", 0, 1234, "/tmp/w1", 120L, 0L),
            McpWorkspaceInfo("def2", "Gemini CLI", "Gemini CLI", "completed", 1, 5678, "/tmp/w2", 60L, 0L),
        )
        val result = agentList.execute(emptyMap()) as ToolResult.Success
        assertTrue(result.text.contains("abc1"))
        assertTrue(result.text.contains("Claude Code"))
        assertTrue(result.text.contains("running"))
        assertTrue(result.text.contains("def2"))
        assertTrue(result.text.contains("completed"))
    }

    // ── AgentStatusTool ───────────────────────────────────────────

    @Test
    fun `agent_status missing workspace_id`() = kotlinx.coroutines.test.runTest {
        val result = agentStatus.execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `agent_status workspace not found`() = kotlinx.coroutines.test.runTest {
        val result = agentStatus.execute(mapOf("workspace_id" to "nonexistent"))
        assertTrue((result as ToolResult.Error).message.contains("not found"))
    }

    @Test
    fun `agent_status returns workspace details`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude", "Claude Code", "running", 0, 1234, "/tmp/w1", 120L, 0L),
        )
        val result = agentStatus.execute(mapOf("workspace_id" to "abc1")) as ToolResult.Success
        assertTrue(result.text.contains("abc1"))
        assertTrue(result.text.contains("Claude"))
        assertTrue(result.text.contains("running"))
        assertTrue(result.text.contains("1234"))
    }

    // ── AgentOutputTool ───────────────────────────────────────────

    @Test
    fun `agent_output missing workspace_id`() = kotlinx.coroutines.test.runTest {
        val result = agentOutput.execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `agent_output workspace not found`() = kotlinx.coroutines.test.runTest {
        val result = agentOutput.execute(mapOf("workspace_id" to "nonexistent"))
        assertTrue((result as ToolResult.Error).message.contains("not found"))
    }

    @Test
    fun `agent_output returns session output`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude", "Claude Code", "running", 0, 1234, "/tmp/w1", 120L, 0L),
        )
        fakeBridge.agentOutput = "Building project...\nDone."
        val result = agentOutput.execute(mapOf("workspace_id" to "abc1")) as ToolResult.Success
        assertTrue(result.text.contains("Building project"))
    }

    @Test
    fun `agent_output no active session`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude", "Claude Code", "completed", -1, -1, "/tmp/w1", 120L, 0L),
        )
        fakeBridge.agentOutput = ""
        val result = agentOutput.execute(mapOf("workspace_id" to "abc1")) as ToolResult.Success
        assertTrue(result.text.contains("No output"))
    }

    // ── AgentDiffTool ─────────────────────────────────────────────

    @Test
    fun `agent_diff missing workspace_id`() = kotlinx.coroutines.test.runTest {
        val result = agentDiff.execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `agent_diff returns diff output`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude", "Claude Code", "running", 0, 1234, "/tmp/w1", 120L, 0L),
        )
        fakeBridge.diffOutput = "diff --git a/foo.txt b/foo.txt\n--- a/foo.txt\n+++ b/foo.txt\n@@ -1 +1 @@\n-old\n+new"
        val result = agentDiff.execute(mapOf("workspace_id" to "abc1")) as ToolResult.Success
        assertTrue(result.text.contains("diff --git"))
    }

    @Test
    fun `agent_diff no changes`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude", "Claude Code", "running", 0, 1234, "/tmp/w1", 120L, 0L),
        )
        fakeBridge.diffOutput = ""
        val result = agentDiff.execute(mapOf("workspace_id" to "abc1")) as ToolResult.Success
        assertTrue(result.text.contains("No uncommitted changes"))
    }

    // ── AgentApproveTool ──────────────────────────────────────────

    @Test
    fun `agent_approve missing workspace_id`() = kotlinx.coroutines.test.runTest {
        val result = agentApprove.execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `agent_approve success`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude", "Claude Code", "running", 0, 1234, "/tmp/w1", 120L, 0L),
        )
        fakeBridge.approveResult = true
        val result = agentApprove.execute(mapOf("workspace_id" to "abc1")) as ToolResult.Success
        assertTrue(result.text.contains("approved"))
    }

    @Test
    fun `agent_approve failure`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude", "Claude Code", "running", 0, 1234, "/tmp/w1", 120L, 0L),
        )
        fakeBridge.approveResult = false
        val result = agentApprove.execute(mapOf("workspace_id" to "abc1")) as ToolResult.Error
        assertTrue(result.message.contains("Failed"))
    }

    @Test
    fun `agent_approve is DANGEROUS risk level`() {
        assertEquals(com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel.DANGEROUS, agentApprove.riskLevel)
    }

    // ── AgentRejectTool ────────────────────────────────────────────

    @Test
    fun `agent_reject missing workspace_id`() = kotlinx.coroutines.test.runTest {
        val result = agentReject.execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `agent_reject success`() = kotlinx.coroutines.test.runTest {
        fakeBridge.workspaces = listOf(
            McpWorkspaceInfo("abc1", "Claude", "Claude Code", "running", 0, 1234, "/tmp/w1", 120L, 0L),
        )
        fakeBridge.rejectResult = true
        val result = agentReject.execute(mapOf("workspace_id" to "abc1")) as ToolResult.Success
        assertTrue(result.text.contains("rejected"))
    }

    @Test
    fun `agent_reject is DANGEROUS risk level`() {
        assertEquals(com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel.DANGEROUS, agentReject.riskLevel)
    }

    // ── Fake bridge ────────────────────────────────────────────────

    private class FakeMcpBridge : McpSessionBridge {
        var workspaces: List<McpWorkspaceInfo> = emptyList()
        var agentOutput: String = ""
        var diffOutput: String = ""
        var approveResult: Boolean = false
        var rejectResult: Boolean = false

        override val sessions: StateFlow<List<com.novaterm.core.mcp.bridge.McpSessionInfo>>
            get() = MutableStateFlow(emptyList())

        override fun createSession(cwd: String?) = 0
        override fun closeSession(index: Int) {}
        override fun writeToSession(index: Int, input: String) {}
        override fun readOutput(index: Int, lines: Int) = agentOutput
        override fun getSessionCwd(index: Int) = "/tmp"
        override fun homePath() = "/home"
        override fun prefixPath() = "/usr"
        override fun readFile(path: String, maxBytes: Int) = null
        override fun writeFile(path: String, content: String) = true
        override fun listDirectory(path: String) = emptyList<com.novaterm.core.mcp.bridge.FileEntry>()

        override fun listWorkspaces() = workspaces
        override fun getWorkspace(workspaceId: String) = workspaces.firstOrNull { it.id == workspaceId }
        override fun getAgentOutput(workspaceId: String, lines: Int) = agentOutput
        override fun runAgentDiff(workspaceId: String) = diffOutput
        override fun approveAgentChanges(workspaceId: String, message: String) = approveResult
        override fun rejectAgentChanges(workspaceId: String) = rejectResult
    }
}