package com.novaterm.feature.terminal.url

/**
 * Detects URLs in terminal text output.
 *
 * Uses a regex pattern based on Alacritty's URL detection, adapted for
 * common terminal output patterns. Handles parenthesized URLs (Wikipedia),
 * URLs with query parameters, and trims trailing punctuation.
 */
object UrlDetector {

    // Matches http(s), ftp, ssh, git, file, mailto URLs.
    // Excludes control chars, whitespace, and common surrounding delimiters.
    private val URL_REGEX = Regex(
        """(https?://|ftp://|ssh://|git://|file://|mailto:)""" +
        """[^\u0000-\u001F\u007F-\u009F<>"'\s{}\\\^`]+"""
    )

    /**
     * Finds a URL at or near the given character offset in the text.
     * Returns the URL string if found, null otherwise.
     */
    fun findUrlAt(text: String, offset: Int): String? {
        return URL_REGEX.findAll(text)
            .firstOrNull { offset in it.range }
            ?.value
            ?.trimTrailingPunctuation()
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

    /**
     * Trims trailing characters that are commonly not part of URLs
     * but appear adjacent in terminal output (e.g., periods at end of
     * sentences, closing parens in markdown).
     */
    private fun String.trimTrailingPunctuation(): String {
        var result = this
        // Balance parentheses: if URL has more closing than opening, trim
        while (result.endsWith(")") && result.count { it == '(' } < result.count { it == ')' }) {
            result = result.dropLast(1)
        }
        // Trim common trailing punctuation
        while (result.isNotEmpty() && result.last() in ".,;:!?") {
            result = result.dropLast(1)
        }
        return result
    }
}
