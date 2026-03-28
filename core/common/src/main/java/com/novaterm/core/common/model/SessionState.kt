package com.novaterm.core.common.model

enum class SessionStatus {
    RUNNING,
    FINISHED,
    CRASHED,
}

data class SessionInfo(
    val id: Int,
    val pid: Int,
    val title: String,
    val cwd: String,
    val status: SessionStatus,
    val exitCode: Int? = null,
)

data class TerminalDimensions(
    val rows: Int,
    val columns: Int,
) {
    init {
        require(rows > 0) { "rows must be positive, got $rows" }
        require(columns > 0) { "columns must be positive, got $columns" }
    }
}

data class CursorPosition(
    val row: Int,
    val column: Int,
)
