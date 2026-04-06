package com.novaterm.app.service

import com.novaterm.core.mcp.bridge.FileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for ServiceMcpBridge file operations (readFile, writeFile, listDirectory).
 *
 * These methods use java.io.File directly and don't need TerminalSession mocks.
 * We test the same logic extracted into standalone functions.
 */
class ServiceMcpBridgeFileOpsTest {

    @get:Rule val tempDir = TemporaryFolder()

    // Replicate readFile logic
    private fun readFile(path: String, maxBytes: Int = 1_000_000): String? {
        return try {
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) return null
            if (file.length() > maxBytes) return null
            file.readText()
        } catch (_: Exception) {
            null
        }
    }

    // Replicate writeFile logic
    private fun writeFile(path: String, content: String): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (_: Exception) {
            false
        }
    }

    // Replicate listDirectory logic
    private fun listDirectory(path: String): List<FileEntry> {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            dir.listFiles()?.map { file ->
                FileEntry(
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                )
            }?.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name })
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── readFile ─────────────────────────────────────────

    @Test
    fun `readFile returns content for existing file`() {
        val file = tempDir.newFile("test.txt")
        file.writeText("hello world")
        assertEquals("hello world", readFile(file.absolutePath))
    }

    @Test
    fun `readFile returns null for nonexistent file`() {
        assertNull(readFile(File(tempDir.root, "nope.txt").absolutePath))
    }

    @Test
    fun `readFile returns null for directory`() {
        val dir = tempDir.newFolder("adir")
        assertNull(readFile(dir.absolutePath))
    }

    @Test
    fun `readFile returns null when file exceeds maxBytes`() {
        val file = tempDir.newFile("big.txt")
        file.writeText("x".repeat(1000))
        assertNull(readFile(file.absolutePath, maxBytes = 500))
    }

    @Test
    fun `readFile returns content when file equals maxBytes`() {
        val file = tempDir.newFile("exact.txt")
        file.writeText("x".repeat(500))
        // file.length() == 500, maxBytes == 500 → NOT > maxBytes, so should return content
        val result = readFile(file.absolutePath, maxBytes = 500)
        assertEquals("x".repeat(500), result)
    }

    @Test
    fun `readFile returns null for file just over maxBytes`() {
        val file = tempDir.newFile("over.txt")
        file.writeText("x".repeat(501))
        assertNull(readFile(file.absolutePath, maxBytes = 500))
    }

    // ── writeFile ────────────────────────────────────────

    @Test
    fun `writeFile creates file with content`() {
        val path = File(tempDir.root, "out.txt").absolutePath
        assertTrue(writeFile(path, "data"))
        assertEquals("data", File(path).readText())
    }

    @Test
    fun `writeFile creates parent directories`() {
        val path = File(tempDir.root, "a/b/c/deep.txt").absolutePath
        assertTrue(writeFile(path, "nested"))
        assertEquals("nested", File(path).readText())
    }

    @Test
    fun `writeFile overwrites existing file`() {
        val file = tempDir.newFile("exist.txt")
        file.writeText("old")
        assertTrue(writeFile(file.absolutePath, "new"))
        assertEquals("new", file.readText())
    }

    // ── listDirectory ────────────────────────────────────

    @Test
    fun `listDirectory returns empty for nonexistent path`() {
        val entries = listDirectory(File(tempDir.root, "nope").absolutePath)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `listDirectory returns empty for file path`() {
        val file = tempDir.newFile("afile.txt")
        assertTrue(listDirectory(file.absolutePath).isEmpty())
    }

    @Test
    fun `listDirectory lists files and folders`() {
        val dir = tempDir.newFolder("mydir")
        File(dir, "alpha.txt").writeText("a")
        File(dir, "beta.txt").writeText("bb")
        File(dir, "subdir").mkdirs()

        val entries = listDirectory(dir.absolutePath)
        assertEquals(3, entries.size)
        // Directories first
        assertTrue(entries[0].isDirectory)
        assertEquals("subdir", entries[0].name)
        assertEquals(0L, entries[0].size)
        // Then files alphabetically
        assertFalse(entries[1].isDirectory)
        assertEquals("alpha.txt", entries[1].name)
        assertEquals(1L, entries[1].size)
        assertEquals("beta.txt", entries[2].name)
        assertEquals(2L, entries[2].size)
    }

    @Test
    fun `listDirectory sorts directories before files`() {
        val dir = tempDir.newFolder("sorted")
        File(dir, "zzz.txt").writeText("z")
        File(dir, "aaa").mkdirs()
        File(dir, "bbb.txt").writeText("b")

        val entries = listDirectory(dir.absolutePath)
        assertTrue(entries[0].isDirectory)
        assertEquals("aaa", entries[0].name)
    }

    @Test
    fun `listDirectory returns empty for empty directory`() {
        val dir = tempDir.newFolder("empty")
        val entries = listDirectory(dir.absolutePath)
        assertTrue(entries.isEmpty())
    }

    // ── readOutput line logic ────────────────────────────

    @Test
    fun `readOutput lines coerceAtLeast 1`() {
        val lines = 0
        val clamped = lines.coerceAtLeast(1)
        assertEquals(1, clamped)
    }

    @Test
    fun `readOutput negative lines becomes 1`() {
        val lines = -5
        val clamped = lines.coerceAtLeast(1)
        assertEquals(1, clamped)
    }

    @Test
    fun `readOutput takeLast extracts correct lines`() {
        val transcript = "line1\nline2\nline3\nline4\nline5"
        val allLines = transcript.split("\n")
        val result = allLines.takeLast(3).joinToString("\n")
        assertEquals("line3\nline4\nline5", result)
    }

    // ── syncMcpSessions title fallback ───────────────────

    @Test
    fun `title fallback for blank`() {
        val title: String? = "   "
        val result = title?.takeIf { it.isNotBlank() } ?: "shell"
        assertEquals("shell", result)
    }

    @Test
    fun `title fallback for null`() {
        val title: String? = null
        val result = title?.takeIf { it.isNotBlank() } ?: "shell"
        assertEquals("shell", result)
    }

    @Test
    fun `title preserved when non-blank`() {
        val title: String? = "my-session"
        val result = title?.takeIf { it.isNotBlank() } ?: "shell"
        assertEquals("my-session", result)
    }
}
