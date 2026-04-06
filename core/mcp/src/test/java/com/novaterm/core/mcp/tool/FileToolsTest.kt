package com.novaterm.core.mcp.tool

import com.novaterm.core.mcp.bridge.FileEntry
import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.bridge.McpSessionInfo
import com.novaterm.core.mcp.tool.builtin.FileReadTool
import com.novaterm.core.mcp.tool.builtin.FileWriteTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileToolsTest {

    @get:Rule val tempDir = TemporaryFolder()

    private fun bridgeWithHome(home: File) = object : McpSessionBridge {
        override val sessions: StateFlow<List<McpSessionInfo>> = MutableStateFlow(emptyList())
        override fun createSession(cwd: String?) = -1
        override fun closeSession(index: Int) {}
        override fun writeToSession(index: Int, input: String) {}
        override fun readOutput(index: Int, lines: Int) = ""
        override fun getSessionCwd(index: Int) = null
        override fun homePath() = home.canonicalPath
        override fun prefixPath() = home.canonicalPath
        override fun readFile(path: String, maxBytes: Int): String? {
            val f = File(path)
            return if (f.exists() && f.isFile) f.readText() else null
        }
        override fun writeFile(path: String, content: String): Boolean {
            return try { File(path).apply { parentFile?.mkdirs() }.writeText(content); true }
            catch (_: Exception) { false }
        }
        override fun listDirectory(path: String) = emptyList<FileEntry>()
    }

    // ── FileReadTool ──────────────────────────────────────

    @Test
    fun `read file with relative path succeeds`() = runTest {
        val home = tempDir.newFolder("home")
        File(home, "test.txt").writeText("hello")
        val tool = FileReadTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "test.txt"))
        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).text.contains("hello"))
    }

    @Test
    fun `read file with tilde path succeeds`() = runTest {
        val home = tempDir.newFolder("home")
        File(home, "test.txt").writeText("world")
        val tool = FileReadTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "~/test.txt"))
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `read file with path traversal is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "../../../etc/passwd"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("traversal"))
    }

    @Test
    fun `read file with tilde traversal is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "~/../../etc/passwd"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("traversal"))
    }

    @Test
    fun `read file with absolute path outside home is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "/system/bin/sh"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `read file missing path param returns error`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))
        val result = tool.execute(emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("path"))
    }

    @Test
    fun `read nonexistent file returns error`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "doesnotexist.txt"))
        assertTrue(result is ToolResult.Error)
    }

    // ── FileWriteTool ─────────────────────────────────────

    @Test
    fun `write file with relative path succeeds`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "out.txt", "content" to "data"))
        assertTrue(result is ToolResult.Success)
        assertTrue(File(home, "out.txt").readText() == "data")
    }

    @Test
    fun `write file with path traversal is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "../../../tmp/evil", "content" to "x"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("traversal"))
    }

    @Test
    fun `write to blocked path is rejected`() = runTest {
        val home = tempDir.newFolder("home")
        // Create a subdirectory that simulates /proc inside home — but the tool
        // resolves canonical path so we test the policy check itself
        val tool = FileWriteTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "/proc/self/mem", "content" to "x"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `write missing content param returns error`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))
        val result = tool.execute(mapOf("path" to "test.txt"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("content"))
    }
}
