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
}
