package com.novaterm.core.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SYMLINKS.txt parsing logic used by BootstrapInstaller.
 * Format: "target←link" where ← is the Unicode left arrow delimiter.
 */
class SymlinksParserTest {

    private fun parseSymlinks(content: String): List<Pair<String, String>> {
        return content.lines()
            .filter { "←" in it }
            .map { line ->
                val parts = line.split("←", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
    }

    @Test
    fun `parses basic symlink entry`() {
        val result = parseSymlinks("bash←bin/sh")
        assertEquals(1, result.size)
        assertEquals("bash" to "bin/sh", result[0])
    }

    @Test
    fun `parses multiple entries`() {
        val content = """
            bash←bin/sh
            dash←bin/dash
            coreutils←bin/ls
        """.trimIndent()
        val result = parseSymlinks(content)
        assertEquals(3, result.size)
        assertEquals("bash", result[0].first)
        assertEquals("bin/sh", result[0].second)
    }

    @Test
    fun `ignores empty lines`() {
        val content = """
            bash←bin/sh

            dash←bin/dash
        """.trimIndent()
        val result = parseSymlinks(content)
        assertEquals(2, result.size)
    }

    @Test
    fun `ignores lines without delimiter`() {
        val content = """
            bash←bin/sh
            this is not a symlink
            dash←bin/dash
        """.trimIndent()
        val result = parseSymlinks(content)
        assertEquals(2, result.size)
    }

    @Test
    fun `handles paths with spaces in target`() {
        val result = parseSymlinks("some target←bin/link")
        assertEquals("some target", result[0].first)
    }

    @Test
    fun `handles deep paths`() {
        val result = parseSymlinks("../../lib/libfoo.so←lib/libfoo.so.1")
        assertEquals("../../lib/libfoo.so", result[0].first)
        assertEquals("lib/libfoo.so.1", result[0].second)
    }

    @Test
    fun `empty content returns empty list`() {
        assertTrue(parseSymlinks("").isEmpty())
        assertTrue(parseSymlinks("\n\n").isEmpty())
    }

    @Test
    fun `real termux symlinks format`() {
        // Actual entries from a Termux bootstrap SYMLINKS.txt
        val content = """
            dash←bin/sh
            bash←bin/rbash
            coreutils←bin/[
            coreutils←bin/base32
            coreutils←bin/base64
        """.trimIndent()
        val result = parseSymlinks(content)
        assertEquals(5, result.size)
        assertEquals("dash" to "bin/sh", result[0])
        assertEquals("coreutils" to "bin/[", result[2])
    }
}
