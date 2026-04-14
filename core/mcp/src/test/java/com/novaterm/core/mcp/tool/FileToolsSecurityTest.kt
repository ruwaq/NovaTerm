package com.novaterm.core.mcp.tool

import com.novaterm.core.mcp.bridge.FileEntry
import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.bridge.McpSessionInfo
import com.novaterm.core.mcp.bridge.McpWorkspaceInfo
import com.novaterm.core.mcp.tool.builtin.FileReadTool
import com.novaterm.core.mcp.tool.builtin.FileWriteTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Security-focused tests for FileReadTool and FileWriteTool.
 *
 * These tools handle real filesystem access — every test here validates
 * a security boundary: path traversal, blocked paths, size limits,
 * blank inputs, and graceful handling of missing files.
 */
class FileToolsSecurityTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    // ── Bridge factory ────────────────────────────────────────────────────────

    /**
     * Builds a minimal [McpSessionBridge] backed by real file I/O on a
     * temporary home directory.  The bridge honours [maxBytesOverride] so
     * we can simulate the max-file-size limit without writing a large file.
     */
    private fun bridgeWithHome(
        home: File,
        maxBytesOverride: Int = 1_000_000,
    ): McpSessionBridge = object : McpSessionBridge {
        override val sessions: StateFlow<List<McpSessionInfo>> =
            MutableStateFlow(emptyList())

        override fun createSession(cwd: String?) = -1
        override fun closeSession(index: Int) {}
        override fun writeToSession(index: Int, input: String) {}
        override fun readOutput(index: Int, lines: Int) = ""
        override fun getSessionCwd(index: Int): String? = null
        override fun homePath() = home.canonicalPath
        override fun prefixPath() = home.canonicalPath

        override fun readFile(path: String, maxBytes: Int): String? {
            val f = File(path)
            if (!f.exists() || !f.isFile) return null
            // Respect the effective byte limit (test override or caller-supplied).
            val limit = minOf(maxBytes, maxBytesOverride)
            if (f.length() > limit) return null
            return f.readText()
        }

        override fun writeFile(path: String, content: String): Boolean = try {
            File(path).apply { parentFile?.mkdirs() }.writeText(content)
            true
        } catch (_: Exception) {
            false
        }

        override fun listDirectory(path: String): List<FileEntry> = emptyList()
        override fun listWorkspaces() = emptyList<McpWorkspaceInfo>()
        override fun getWorkspace(workspaceId: String): McpWorkspaceInfo? = null
        override fun getAgentOutput(workspaceId: String, lines: Int) = ""
        override fun runAgentDiff(workspaceId: String) = ""
        override fun approveAgentChanges(workspaceId: String, message: String) = false
        override fun rejectAgentChanges(workspaceId: String) = false
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun errorMessage(result: ToolResult): String =
        (result as ToolResult.Error).message

    // ═══════════════════════════════════════════════════════════════════════
    // FileReadTool — path traversal
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `read - relative path traversal with dotdot is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "../../../etc/passwd"))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("traversal"))
    }

    @Test
    fun `read - tilde path traversal with dotdot is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "~/../../etc/shadow"))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("traversal"))
    }

    @Test
    fun `read - absolute path outside home is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/etc/passwd"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `read - deeply nested dotdot sequence is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        // Many levels up from a deep relative path.
        val result = tool.execute(mapOf("path" to "a/b/c/../../../../../../../../etc/shadow"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `read - path that resolves to home itself is allowed`() = runTest {
        // Edge case: canonical == homeCanonical (not just starts-with).
        // FileReadTool allows this (canonical == homeCanonical), though
        // readFile will return null because home is a directory, not a file.
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        // Resolve to home via redundant dotdot inside home.
        val result = tool.execute(mapOf("path" to "subdir/../"))
        // Either success (if bridge can read a dir) or error about not-found —
        // the important thing is it does NOT error as path traversal.
        if (result is ToolResult.Error) {
            assertFalse(errorMessage(result).contains("traversal"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileReadTool — blocked system paths
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `read - slash etc slash shadow is blocked by security policy`() = runTest {
        // /etc is not in BLOCKED_PATHS, but the path is outside home → traversal
        // check fires first.  We test /proc which IS in BLOCKED_PATHS and also
        // triggers the policy check.
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/proc/1/cmdline"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `read - slash sys path is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/sys/class/power_supply/battery/capacity"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `read - slash vendor path is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/vendor/lib64/libvulkan.so"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `read - slash system path is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/system/bin/sh"))

        assertTrue(result is ToolResult.Error)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileReadTool — max file size
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `read - file exceeding size limit returns error`() = runTest {
        val home = tempDir.newFolder("home")
        // Set a 10-byte limit so any file > 10 bytes is rejected.
        val tool = FileReadTool(bridgeWithHome(home, maxBytesOverride = 10))

        val bigFile = File(home, "big.txt").apply {
            writeText("A".repeat(11)) // 11 bytes > limit
        }

        val result = tool.execute(mapOf("path" to bigFile.name))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `read - file exactly at size limit succeeds`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home, maxBytesOverride = 10))

        val exactFile = File(home, "exact.txt").apply {
            writeText("A".repeat(10)) // exactly 10 bytes
        }

        val result = tool.execute(mapOf("path" to exactFile.name))

        assertTrue(result is ToolResult.Success)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileReadTool — missing / blank parameters
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `read - missing path parameter returns error mentioning path`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(emptyMap())

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("path"))
    }

    @Test
    fun `read - null path value returns error`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to null))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("path"))
    }

    @Test
    fun `read - blank path resolves to home and returns error not-found`() = runTest {
        // An empty string is treated as a relative path → bridge.homePath() + "/" + ""
        // which canonicalizes to home itself (a directory, not a file).
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to ""))

        // Should not crash; either a traversal block or a not-found error.
        assertTrue(result is ToolResult.Error)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileReadTool — nonexistent files
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `read - nonexistent file inside home returns error`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "ghost.txt"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `read - nonexistent nested path inside home returns error`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileReadTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "a/b/c/nope.txt"))

        assertTrue(result is ToolResult.Error)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileWriteTool — path traversal
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `write - relative dotdot traversal is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "../../../tmp/evil", "content" to "x"))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("traversal"))
    }

    @Test
    fun `write - tilde dotdot traversal is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "~/../../tmp/evil", "content" to "x"))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("traversal"))
    }

    @Test
    fun `write - absolute path outside home is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/tmp/injected.sh", "content" to "malicious"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `write - no file is created when traversal is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val sibling = tempDir.newFolder("sibling")
        val tool = FileWriteTool(bridgeWithHome(home))

        tool.execute(mapOf("path" to "../sibling/evil.txt", "content" to "x"))

        // Sibling directory must remain untouched.
        assertFalse(File(sibling, "evil.txt").exists())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileWriteTool — blocked system paths
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `write - slash proc path is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/proc/self/mem", "content" to "x"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `write - slash sys path is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/sys/kernel/debug/foo", "content" to "x"))

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `write - slash system path is blocked`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "/system/app/NovaTerm.apk", "content" to "x"))

        assertTrue(result is ToolResult.Error)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileWriteTool — parent directory creation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `write - creates parent directories when they do not exist`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(
            mapOf("path" to "deep/nested/dir/file.txt", "content" to "hello"),
        )

        assertTrue(result is ToolResult.Success)
        assertTrue(File(home, "deep/nested/dir/file.txt").exists())
        assertTrue(File(home, "deep/nested/dir/file.txt").readText() == "hello")
    }

    @Test
    fun `write - overwrites existing file`() = runTest {
        val home = tempDir.newFolder("home")
        File(home, "existing.txt").writeText("old content")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "existing.txt", "content" to "new content"))

        assertTrue(result is ToolResult.Success)
        assertTrue(File(home, "existing.txt").readText() == "new content")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileWriteTool — empty content
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `write - empty string content is allowed`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "empty.txt", "content" to ""))

        assertTrue(result is ToolResult.Success)
        assertTrue(File(home, "empty.txt").exists())
        assertTrue(File(home, "empty.txt").readText().isEmpty())
    }

    @Test
    fun `write - success message includes byte count`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))
        val payload = "hello world" // 11 chars

        val result = tool.execute(mapOf("path" to "out.txt", "content" to payload))

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).text.contains("11"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FileWriteTool — missing / blank parameters
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `write - missing path parameter returns error mentioning path`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("content" to "x"))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("path"))
    }

    @Test
    fun `write - missing content parameter returns error mentioning content`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "test.txt"))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("content"))
    }

    @Test
    fun `write - null path value returns error`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to null, "content" to "x"))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("path"))
    }

    @Test
    fun `write - null content value returns error`() = runTest {
        val home = tempDir.newFolder("home")
        val tool = FileWriteTool(bridgeWithHome(home))

        val result = tool.execute(mapOf("path" to "test.txt", "content" to null))

        assertTrue(result is ToolResult.Error)
        assertTrue(errorMessage(result).contains("content"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Both tools — valid happy-path sanity checks
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `read and write roundtrip with relative path succeeds`() = runTest {
        val home = tempDir.newFolder("home")
        val bridge = bridgeWithHome(home)
        val reader = FileReadTool(bridge)
        val writer = FileWriteTool(bridge)

        val writeResult = writer.execute(mapOf("path" to "roundtrip.txt", "content" to "ping"))
        assertTrue(writeResult is ToolResult.Success)

        val readResult = reader.execute(mapOf("path" to "roundtrip.txt"))
        assertTrue(readResult is ToolResult.Success)
        assertTrue((readResult as ToolResult.Success).text == "ping")
    }

    @Test
    fun `read and write roundtrip with tilde path succeeds`() = runTest {
        val home = tempDir.newFolder("home")
        val bridge = bridgeWithHome(home)
        val reader = FileReadTool(bridge)
        val writer = FileWriteTool(bridge)

        writer.execute(mapOf("path" to "~/tilde.txt", "content" to "tilde-ok"))
        val readResult = reader.execute(mapOf("path" to "~/tilde.txt"))

        assertTrue(readResult is ToolResult.Success)
        assertTrue((readResult as ToolResult.Success).text == "tilde-ok")
    }
}
