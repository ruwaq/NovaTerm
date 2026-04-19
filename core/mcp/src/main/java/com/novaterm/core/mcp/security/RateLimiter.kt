package com.novaterm.core.mcp.security

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Per-client sliding-window rate limiter.
 *
 * Tracks request timestamps per remote address so one misbehaving client
 * cannot starve others. Each client gets its own window of [maxRequests]
 * within [windowMs] milliseconds.
 *
 * Thread-safe for concurrent access from Ktor coroutines.
 *
 * @param maxRequests Maximum number of requests allowed per window per client.
 * @param windowMs   Window duration in milliseconds (default: 1 minute).
 * @param maxClients  Maximum number of tracked clients. Least-recently-seen
 *                    clients are evicted when this limit is exceeded.
 */
class RateLimiter(
    val maxRequests: Int = 60,
    private val windowMs: Long = 60_000L,
    private val maxClients: Int = 256,
) {
    private data class ClientBucket(
        val timestamps: ConcurrentLinkedDeque<Long> = ConcurrentLinkedDeque(),
        @Volatile var lastSeen: Long = 0,
    )

    private val buckets = ConcurrentHashMap<String, ClientBucket>()

    /**
     * Attempt to acquire a request slot for [clientId].
     *
     * @return `true` if the request is allowed, `false` if rate-limited.
     */
    fun tryAcquire(clientId: String): Boolean {
        val now = System.currentTimeMillis()
        val bucket = getOrCreateBucket(clientId, now)
        bucket.lastSeen = now

        synchronized(bucket) {
            val cutoff = now - windowMs
            while (bucket.timestamps.peekFirst()?.let { it < cutoff } == true) {
                bucket.timestamps.pollFirst()
            }
            if (bucket.timestamps.size >= maxRequests) return false
            bucket.timestamps.addLast(now)
            return true
        }
    }

    private fun getOrCreateBucket(clientId: String, now: Long): ClientBucket {
        return buckets.getOrPut(clientId) {
            // Evict stale clients if at capacity
            if (buckets.size >= maxClients) {
                val threshold = now - windowMs * 2
                buckets.entries.removeIf { it.value.lastSeen < threshold }
            }
            ClientBucket()
        }
    }

    /** Current number of tracked clients. */
    val clientCount: Int get() = buckets.size

    /** Remove all tracking state. */
    fun clear() = buckets.clear()
}