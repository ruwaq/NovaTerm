package com.novaterm.core.mcp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `allows requests under the limit`() {
        val limiter = RateLimiter(maxRequests = 5, windowMs = 60_000)
        repeat(5) { i ->
            assertTrue("Request ${i + 1} should be allowed", limiter.tryAcquire("client1"))
        }
    }

    @Test
    fun `rejects requests over the limit`() {
        val limiter = RateLimiter(maxRequests = 3, windowMs = 60_000)
        repeat(3) { limiter.tryAcquire("client1") }
        assertFalse("4th request should be rejected", limiter.tryAcquire("client1"))
        assertFalse("5th request should also be rejected", limiter.tryAcquire("client1"))
    }

    @Test
    fun `allows requests after window expires`() {
        val limiter = RateLimiter(maxRequests = 2, windowMs = 1)
        assertTrue(limiter.tryAcquire("client1"))
        assertTrue(limiter.tryAcquire("client1"))

        // Wait for window to expire
        Thread.sleep(5)

        assertTrue("Should allow after window expires", limiter.tryAcquire("client1"))
    }

    @Test
    fun `per-client isolation - one client does not affect another`() {
        val limiter = RateLimiter(maxRequests = 2, windowMs = 60_000)
        assertTrue(limiter.tryAcquire("client1"))
        assertTrue(limiter.tryAcquire("client1"))
        assertFalse("client1 should be rate limited", limiter.tryAcquire("client1"))
        assertTrue("client2 should not be affected", limiter.tryAcquire("client2"))
        assertTrue("client2 second request", limiter.tryAcquire("client2"))
        assertFalse("client2 should be rate limited", limiter.tryAcquire("client2"))
    }

    @Test
    fun `default configuration allows 60 requests per minute`() {
        val limiter = RateLimiter()
        repeat(60) { i ->
            assertTrue("Request ${i + 1} should be allowed", limiter.tryAcquire("client1"))
        }
        assertFalse("61st request should be rejected", limiter.tryAcquire("client1"))
    }

    @Test
    fun `concurrent access does not crash`() {
        val limiter = RateLimiter(maxRequests = 1000, windowMs = 60_000)
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) { limiter.tryAcquire("client$threadId") }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(10, limiter.clientCount)
    }
}