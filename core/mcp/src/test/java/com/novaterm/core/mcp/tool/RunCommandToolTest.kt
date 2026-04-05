package com.novaterm.core.mcp.tool

import com.novaterm.core.mcp.bridge.FileEntry
import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.bridge.McpSessionInfo
import com.novaterm.core.mcp.tool.builtin.RunCommandTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCommandToolTest {

    /** Bridge that simulates output appearing after a short delay. */
    private class FakeBridge(
        private val outputByRead: List<String>,
    ) : McpSessionBridge {
        private var readCount = 0
        val writtenInput = mutableListOf<String>()

        override val sessions: StateFlow<List<McpSessionInfo>> =
            MutableStateFlow(listOf(McpSessionInfo(0, "shell", "/", true, 1)))

        override fun createSession(cwd: String?) = 0
        override fun closeSession(index: Int) {}
        override fun writeToSession(index: Int, input: String) { writtenInput += input }
        override fun readOutput(index: Int, lines: Int): String =
            outputByRead.getOrElse(readCount++) { outputByRead.last() }
        override fun getSessionCwd(index: Int) = "/"
        override fun homePath() = "/home"
        override fun prefixPath() = "/usr"
        override fun readFile(path: String, maxBytes: Int) = null
        override fun writeFile(path: String, content: String) = false
        override fun listDirectory(path: String) = emptyList<FileEntry>()
    }

    @Test
    fun `command is written to session`() = runTest {
        val bridge = FakeBridge(listOf("output", "output"))
        val tool = RunCommandTool(bridge)
        tool.execute(mapOf("command" to "ls"))
        assertEquals("ls\n", bridge.writtenInput.first())
    }

    @Test
    fun `returns stable output on consecutive identical reads`() = runTest {
        // Output appears empty → then "result" → stable on second "result"
        val bridge = FakeBridge(listOf("", "result", "result"))
        val tool = RunCommandTool(bridge)
        val result = tool.execute(mapOf("command" to "echo hi"))
        assertTrue(result is ToolResult.Success)
        assertEquals("result", (result as ToolResult.Success).text)
    }

    @Test
    fun `blocked command returns error`() = runTest {
        val bridge = FakeBridge(listOf(""))
        val tool = RunCommandTool(bridge)
        val result = tool.execute(mapOf("command" to "rm -rf /"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("blocked"))
    }

    @Test
    fun `missing command parameter returns error`() = runTest {
        val bridge = FakeBridge(listOf(""))
        val tool = RunCommandTool(bridge)
        val result = tool.execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `wait_ms is clamped to valid range`() = runTest {
        // Very short wait should still succeed (just timeout path)
        val bridge = FakeBridge(listOf("partial"))
        val tool = RunCommandTool(bridge)
        val result = tool.execute(mapOf("command" to "slow", "wait_ms" to 500))
        assertTrue(result is ToolResult.Success)
    }
}
