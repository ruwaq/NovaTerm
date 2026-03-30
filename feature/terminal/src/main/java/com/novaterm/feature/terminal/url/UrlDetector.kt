package com.novaterm.feature.terminal.url

/**
 * Detects tappable entities in terminal text: URLs, IP addresses, file paths.
 *
 * Uses regex patterns adapted from Alacritty and Kitty terminal detectors.
 * Handles parenthesized URLs, IPv6 brackets, and trims trailing punctuation.
 */
object UrlDetector {

    /** Detected entity with type and value. */
    data class Entity(val type: Type, val value: String) {
        enum class Type { URL, IP_ADDRESS, FILE_PATH }
    }

    // ── URL pattern ──────────────────────────────────────────
    private val URL_REGEX = Regex(
        """(https?://|ftp://|ssh://|git://|file://|mailto:)""" +
        """[^\u0000-\u001F\u007F-\u009F<>"'\s{}\\\^`]+"""
    )

    // ── IP address patterns ──────────────────────────────────
    // IPv4: 192.168.1.1 or 192.168.1.1:8080
    private val IPV4_REGEX = Regex(
        """(?<!\w)(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(:\d{1,5})?(?!\w)"""
    )

    // IPv6: [::1] or [2001:db8::1]:8080
    private val IPV6_REGEX = Regex(
        """\[([0-9a-fA-F:]+)\](:\d{1,5})?"""
    )

    // localhost:port
    private val LOCALHOST_REGEX = Regex(
        """(?<!\w)localhost(:\d{1,5})(?!\w)"""
    )

    // ── File path pattern ────────────────────────────────────
    // Absolute paths: /usr/bin/bash, ~/projects/foo, ./relative
    // Avoids matching lone / or common false positives
    private val PATH_REGEX = Regex(
        """(?<!\w)(~?\.?/[a-zA-Z0-9._\-]+(?:/[a-zA-Z0-9._\-]+)+)"""
    )

    // ── Combined detection ───────────────────────────────────

    /**
     * Finds a tappable entity at the given character offset.
     * Priority: URL > IP > Path
     */
    fun findEntityAt(text: String, offset: Int): Entity? {
        // URL first (highest priority)
        URL_REGEX.findAll(text).firstOrNull { offset in it.range }?.let {
            return Entity(Entity.Type.URL, it.value.trimTrailingPunctuation())
        }

        // IP addresses
        IPV4_REGEX.findAll(text).firstOrNull { offset in it.range }?.let { match ->
            val ip = match.groupValues[1]
            if (isValidIpv4(ip)) {
                return Entity(Entity.Type.IP_ADDRESS, match.value)
            }
        }

        IPV6_REGEX.findAll(text).firstOrNull { offset in it.range }?.let {
            return Entity(Entity.Type.IP_ADDRESS, it.value)
        }

        LOCALHOST_REGEX.findAll(text).firstOrNull { offset in it.range }?.let {
            return Entity(Entity.Type.IP_ADDRESS, it.value)
        }

        // File paths
        PATH_REGEX.findAll(text).firstOrNull { offset in it.range }?.let {
            return Entity(Entity.Type.FILE_PATH, it.value)
        }

        return null
    }

    // ── Legacy API (backward compatible) ─────────────────────

    /**
     * Finds a URL at or near the given character offset.
     * Returns the URL string if found, null otherwise.
     */
    fun findUrlAt(text: String, offset: Int): String? {
        val entity = findEntityAt(text, offset)
        return when (entity?.type) {
            Entity.Type.URL -> entity.value
            Entity.Type.IP_ADDRESS -> "http://${entity.value}"
            else -> null
        }
    }

    /**
     * Extracts all URLs from the given text.
     */
    fun extractUrls(text: String): List<String> {
        return URL_REGEX.findAll(text)
            .map { it.value.trimTrailingPunctuation() }
            .toList()
    }

    /**
     * Checks if the given string looks like a URL.
     */
    fun isUrl(text: String): Boolean {
        return URL_REGEX.matches(text) || URL_REGEX.containsMatchIn(text)
    }

    // ── Helpers ──────────────────────────────────────────────

    /** Validate IPv4 octets are 0-255. */
    private fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull() ?: return false
            n in 0..255
        }
    }

    /**
     * Trims trailing characters that are commonly not part of URLs
     * but appear adjacent in terminal output.
     */
    private fun String.trimTrailingPunctuation(): String {
        var result = this
        while (result.endsWith(")") && result.count { it == '(' } < result.count { it == ')' }) {
            result = result.dropLast(1)
        }
        while (result.isNotEmpty() && result.last() in ".,;:!?") {
            result = result.dropLast(1)
        }
        return result
    }
}
