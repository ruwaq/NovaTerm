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
) {
    init {
        require(id > 0) { "id must be positive, got $id" }
        require(pid >= 0) { "pid must be non-negative, got $pid" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(cwd.isNotBlank()) { "cwd must not be blank" }
        require(exitCode == null || status != SessionStatus.RUNNING) {
            "RUNNING session must not have an exitCode"
        }
    }
}

data class TerminalDimensions(
    val rows: Int,
    val columns: Int,
) {
    init {
        require(rows in 1..MAX_ROWS) { "rows must be in 1..$MAX_ROWS, got $rows" }
        require(columns in 1..MAX_COLS) { "columns must be in 1..$MAX_COLS, got $columns" }
    }

    companion object {
        const val MAX_ROWS = 500
        const val MAX_COLS = 500
    }
}

data class CursorPosition(
    val row: Int,
    val column: Int,
) {
    init {
        require(row >= 0) { "row must be non-negative, got $row" }
        require(column >= 0) { "column must be non-negative, got $column" }
    }
}
