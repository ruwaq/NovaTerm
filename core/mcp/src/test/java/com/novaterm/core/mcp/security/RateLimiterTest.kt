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
            assertTrue("Request ${i + 1} should be allowed", limiter.tryAcquire())
        }
    }

    @Test
    fun `rejects requests over the limit`() {
        val limiter = RateLimiter(maxRequests = 3, windowMs = 60_000)
        repeat(3) { limiter.tryAcquire() }
        assertFalse("4th request should be rejected", limiter.tryAcquire())
        assertFalse("5th request should also be rejected", limiter.tryAcquire())
    }

    @Test
    fun `allows requests after window expires`() {
        // Use a tiny window so entries expire immediately
        val limiter = RateLimiter(maxRequests = 2, windowMs = 1)
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())

        // Wait for window to expire
        Thread.sleep(5)

        assertTrue("Should allow after window expires", limiter.tryAcquire())
    }

    @Test
    fun `currentCount reflects active requests`() {
        val limiter = RateLimiter(maxRequests = 10, windowMs = 60_000)
        assertEquals(0, limiter.currentCount)
        limiter.tryAcquire()
        limiter.tryAcquire()
        assertEquals(2, limiter.currentCount)
    }

    @Test
    fun `default configuration allows 60 requests per minute`() {
        val limiter = RateLimiter()
        repeat(60) { i ->
            assertTrue("Request ${i + 1} should be allowed", limiter.tryAcquire())
        }
        assertFalse("61st request should be rejected", limiter.tryAcquire())
    }

    @Test
    fun `concurrent access does not crash`() {
        val limiter = RateLimiter(maxRequests = 1000, windowMs = 60_000)
        val threads = (1..10).map {
            Thread {
                repeat(50) { limiter.tryAcquire() }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // All 500 requests should have been tracked (under the 1000 limit)
        assertTrue(limiter.currentCount <= 1000)
    }
}
