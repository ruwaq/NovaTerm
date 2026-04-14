package com.novaterm.core.common.model

object ConfigConstants {
    // Terminal UI constants
    const val DEFAULT_FONT_SIZE: Int = 12
    val FONT_SIZE_RANGE: IntRange = 8..32

    // Session constants
    const val DEFAULT_SCROLLBACK_LINES: Int = 10_000
    val SCROLLBACK_RANGE: IntRange = 0..100_000

    // Engine constants
    const val MAX_SESSION_PERSISTENCE_INTERVAL_MS: Long = 30_000

    // Performance constants
    const val MAX_DRAW_CALLS_PER_FRAME: Int = 2

    // Rendering constants
    const val DEFAULT_TEXT_LINES_PER_SCREEN: Int = 50
    const val DEFAULT_TAB_WIDTH: Int = 8

    // Network constants
    const val DEFAULT_PORT: Int = 9999

    // AI integration constants
    const val DEFAULT_AI_SCROLL_SPEED: Float = 1.0f
}