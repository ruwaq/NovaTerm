package com.novaterm.feature.terminal.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDetectorTest {

    @Test
    fun `detects https URL`() {
        val urls = UrlDetector.extractUrls("Visit https://example.com for more")
        assertEquals(1, urls.size)
        assertEquals("https://example.com", urls[0])
    }

    @Test
    fun `detects http URL`() {
        val urls = UrlDetector.extractUrls("Go to http://localhost:3000/api")
        assertEquals("http://localhost:3000/api", urls[0])
    }

    @Test
    fun `detects URL with path and query`() {
        val urls = UrlDetector.extractUrls("https://example.com/path?q=test&page=1")
        assertEquals("https://example.com/path?q=test&page=1", urls[0])
    }

    @Test
    fun `trims trailing period`() {
        val urls = UrlDetector.extractUrls("See https://example.com.")
        assertEquals("https://example.com", urls[0])
    }

    @Test
    fun `trims trailing comma`() {
        val urls = UrlDetector.extractUrls("URL: https://example.com, then continue")
        assertEquals("https://example.com", urls[0])
    }

    @Test
    fun `handles parenthesized URL (Wikipedia)`() {
        val urls = UrlDetector.extractUrls("(https://en.wikipedia.org/wiki/Rust_(programming_language))")
        assertEquals("https://en.wikipedia.org/wiki/Rust_(programming_language)", urls[0])
    }

    @Test
    fun `detects multiple URLs`() {
        val text = "https://a.com and https://b.com"
        val urls = UrlDetector.extractUrls(text)
        assertEquals(2, urls.size)
    }

    @Test
    fun `findUrlAt returns URL at offset`() {
        val text = "See https://example.com for info"
        val url = UrlDetector.findUrlAt(text, 10)
        assertNotNull(url)
        assertEquals("https://example.com", url)
    }

    @Test
    fun `findUrlAt returns null outside URL`() {
        val text = "See https://example.com for info"
        assertNull(UrlDetector.findUrlAt(text, 0))
        assertNull(UrlDetector.findUrlAt(text, 30))
    }

    @Test
    fun `detects ssh URL`() {
        val urls = UrlDetector.extractUrls("ssh://user@host:22/path")
        assertTrue(urls.isNotEmpty())
    }

    @Test
    fun `detects git URL`() {
        val urls = UrlDetector.extractUrls("git://github.com/user/repo.git")
        assertTrue(urls.isNotEmpty())
    }

    @Test
    fun `detects file URL`() {
        val urls = UrlDetector.extractUrls("file:///home/user/doc.txt")
        assertTrue(urls.isNotEmpty())
    }

    @Test
    fun `ignores plain text without URL`() {
        val urls = UrlDetector.extractUrls("just some plain text without links")
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `isUrl returns true for URL`() {
        assertTrue(UrlDetector.isUrl("https://example.com"))
    }

    @Test
    fun `isUrl returns false for plain text`() {
        assertTrue(!UrlDetector.isUrl("not a url"))
    }

    // ── Entity detection: IP addresses ───────────────────────

    @Test
    fun `findEntityAt detects IPv4`() {
        val text = "Server at 192.168.1.100 is running"
        val entity = UrlDetector.findEntityAt(text, 12)
        assertNotNull(entity)
        assertEquals(UrlDetector.Entity.Type.IP_ADDRESS, entity!!.type)
        assertEquals("192.168.1.100", entity.value)
    }

    @Test
    fun `findEntityAt detects IPv4 with port`() {
        val text = "Connect to 10.0.0.1:8080 for API"
        val entity = UrlDetector.findEntityAt(text, 14)
        assertNotNull(entity)
        assertEquals(UrlDetector.Entity.Type.IP_ADDRESS, entity!!.type)
        assertEquals("10.0.0.1:8080", entity.value)
    }

    @Test
    fun `findEntityAt rejects invalid IPv4`() {
        val text = "Version 999.999.999.999 is out"
        val entity = UrlDetector.findEntityAt(text, 10)
        assertNull(entity)
    }

    @Test
    fun `findEntityAt detects localhost with port`() {
        val text = "Running on localhost:3000"
        val entity = UrlDetector.findEntityAt(text, 15)
        assertNotNull(entity)
        assertEquals(UrlDetector.Entity.Type.IP_ADDRESS, entity!!.type)
        assertEquals("localhost:3000", entity.value)
    }

    @Test
    fun `findEntityAt detects IPv6`() {
        val text = "Listening on [::1]:8080"
        val entity = UrlDetector.findEntityAt(text, 16)
        assertNotNull(entity)
        assertEquals(UrlDetector.Entity.Type.IP_ADDRESS, entity!!.type)
    }

    // ── Entity detection: file paths ─────────────────────────

    @Test
    fun `findEntityAt detects absolute path`() {
        val text = "Error in /usr/local/bin/node at line 42"
        val entity = UrlDetector.findEntityAt(text, 12)
        assertNotNull(entity)
        assertEquals(UrlDetector.Entity.Type.FILE_PATH, entity!!.type)
        assertEquals("/usr/local/bin/node", entity.value)
    }

    @Test
    fun `findEntityAt detects home path`() {
        val text = "Config at ~/projects/novaterm/CLAUDE.md"
        val entity = UrlDetector.findEntityAt(text, 15)
        assertNotNull(entity)
        assertEquals(UrlDetector.Entity.Type.FILE_PATH, entity!!.type)
        assertEquals("~/projects/novaterm/CLAUDE.md", entity.value)
    }

    @Test
    fun `findEntityAt detects relative path`() {
        val text = "See ./src/main/java/App.kt for details"
        val entity = UrlDetector.findEntityAt(text, 8)
        assertNotNull(entity)
        assertEquals(UrlDetector.Entity.Type.FILE_PATH, entity!!.type)
        assertEquals("./src/main/java/App.kt", entity.value)
    }

    @Test
    fun `findEntityAt prioritizes URL over path`() {
        val text = "Visit https://example.com/path/to/page"
        val entity = UrlDetector.findEntityAt(text, 15)
        assertNotNull(entity)
        assertEquals(UrlDetector.Entity.Type.URL, entity!!.type)
    }

    @Test
    fun `findEntityAt returns null for plain text`() {
        assertNull(UrlDetector.findEntityAt("just some words", 5))
    }

    // ── findUrlAt wraps IP as http:// ────────────────────────

    @Test
    fun `findUrlAt wraps IP as http URL`() {
        val text = "Server at 192.168.1.1:3000 running"
        val url = UrlDetector.findUrlAt(text, 14)
        assertEquals("http://192.168.1.1:3000", url)
    }
}
