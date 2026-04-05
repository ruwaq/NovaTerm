package com.novaterm.core.mcp.security

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Sliding-window rate limiter using a token bucket approach.
 *
 * Tracks request timestamps in a [ConcurrentLinkedDeque] and rejects
 * requests that exceed [maxRequests] within [windowMs] milliseconds.
 * Thread-safe for concurrent access from Ktor coroutines.
 *
 * @param maxRequests Maximum number of requests allowed per window.
 * @param windowMs   Window duration in milliseconds (default: 1 minute).
 */
class RateLimiter(
    private val maxRequests: Int = 60,
    private val windowMs: Long = 60_000L,
) {
    private val timestamps = ConcurrentLinkedDeque<Long>()

    /**
     * Attempt to acquire a request slot.
     *
     * @return `true` if the request is allowed, `false` if rate-limited.
     */
    fun tryAcquire(): Boolean {
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs

        // Remove expired entries from the front of the deque
        while (timestamps.peekFirst()?.let { it < cutoff } == true) {
            timestamps.pollFirst()
        }

        if (timestamps.size >= maxRequests) return false

        timestamps.addLast(now)
        return true
    }

    /** Current number of requests tracked in the active window. */
    val currentCount: Int get() = timestamps.size
}
