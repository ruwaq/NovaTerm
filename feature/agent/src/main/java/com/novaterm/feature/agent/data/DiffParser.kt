package com.novaterm.feature.agent.data

/**
 * Parses unified diff output from `git diff` into structured data
 * suitable for rendering in a Compose UI with color-coded lines.
 *
 * Supports:
 * - Standard unified diff format (git diff)
 * - File additions, modifications, deletions, and renames
 * - Multi-hunk diffs
 * - Binary file markers
 * - No-newline-at-end-of-file markers
 *
 * Not supported (out of scope):
 * - --color-words output (use standard unified format instead)
 * - Combined diffs (merge conflicts)
 * - Inter diffs (git diff --inter-hunk-context)
 */
object DiffParser {

    // ── Data model ──────────────────────────────────────────────

    data class DiffResult(
        val files: List<DiffFile>,
        val stats: DiffStats,
    )

    data class DiffStats(
        val filesChanged: Int,
        val insertions: Int,
        val deletions: Int,
    ) {
        val summary: String
            get() = buildString {
                append("$filesChanged file")
                if (filesChanged != 1) append('s')
                append(" changed")
                if (insertions > 0) append(", $insertions insertion(+)")
                if (deletions > 0) append(", $deletions deletion(-)")
            }
    }

    data class DiffFile(
        val oldPath: String,
        val newPath: String,
        val hunks: List<DiffHunk>,
        val isBinary: Boolean = false,
        val isDeleted: Boolean = false,
        val isNew: Boolean = false,
        val isRenamed: Boolean = false,
    ) {
        val displayPath: String
            get() = when {
                isRenamed -> "$oldPath → $newPath"
                isDeleted -> oldPath
                else -> newPath
            }

        val additions: Int
            get() = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.ADD } }

        val deletions: Int
            get() = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.REMOVE } }
    }

    data class DiffHunk(
        val header: String,
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
        val lines: List<DiffLine>,
    )

    data class DiffLine(
        val type: DiffLineType,
        val content: String,
        val oldLineNumber: Int = -1,
        val newLineNumber: Int = -1,
    )

    enum class DiffLineType {
        /** Context line (unchanged) */
        CONTEXT,
        /** Added line */
        ADD,
        /** Removed line */
        REMOVE,
        /** Hunk header (@@ ... @@) */
        HUNK_HEADER,
        /** File header (diff --git ...) */
        FILE_HEADER,
        /** Index/mode line */
        META,
        /** No newline at end of file marker */
        NO_NEWLINE,
    }

    // ── Public API ───────────────────────────────────────────────

    fun parse(diffOutput: String): DiffResult {
        if (diffOutput.isBlank()) {
            return DiffResult(files = emptyList(), stats = DiffStats(0, 0, 0))
        }

        val lines = diffOutput.lines()
        val files = mutableListOf<DiffFile>()
        var currentFile: DiffFileBuilder? = null
        var currentHunk: DiffHunkBuilder? = null
        var oldLine = 0
        var newLine = 0

        for (line in lines) {
            when {
                // File header: diff --git a/foo b/bar
                line.startsWith("diff --git ") -> {
                    // Flush previous file
                    currentHunk?.let { currentFile?.addHunk(it) }
                    currentFile?.let { files.add(it.build()) }

                    val (old, new) = parseFileHeader(line)
                    currentFile = DiffFileBuilder(old, new)
                    currentHunk = null
                    currentFile?.addLine(DiffLine(DiffLineType.FILE_HEADER, line))
                }

                // Old file path: --- a/foo
                line.startsWith("--- ") -> {
                    currentFile?.let { cf ->
                        cf.oldPath = line.removePrefix("--- ").trim()
                        if (line.contains("/dev/null")) cf.isNew = true
                        cf.addLine(DiffLine(DiffLineType.META, line))
                    }
                }

                // New file path: +++ b/bar
                line.startsWith("+++ ") -> {
                    currentFile?.let { cf ->
                        cf.newPath = line.removePrefix("+++ ").trim()
                        if (line.contains("/dev/null")) cf.isDeleted = true
                        // Detect rename: strip a/ b/ prefixes before comparing
                        val oldStripped = cf.oldPath.removePrefix("a/")
                        val newStripped = cf.newPath.removePrefix("b/")
                        if (oldStripped != newStripped
                            && !cf.oldPath.contains("/dev/null")
                            && !cf.newPath.contains("/dev/null")
                        ) {
                            cf.isRenamed = true
                        }
                        cf.addLine(DiffLine(DiffLineType.META, line))
                    }
                }

                // Hunk header: @@ -oldStart,oldCount +newStart,newCount @@
                line.startsWith("@@") -> {
                    // Flush previous hunk
                    currentHunk?.let { currentFile?.addHunk(it) }

                    val parsed = parseHunkHeader(line)
                    currentHunk = DiffHunkBuilder(parsed.header, parsed.oldStart, parsed.oldCount, parsed.newStart, parsed.newCount)
                    oldLine = parsed.oldStart
                    newLine = parsed.newStart
                    currentHunk?.addLine(DiffLine(DiffLineType.HUNK_HEADER, line))
                }

                // Added line
                line.startsWith("+") -> {
                    currentHunk?.addLine(DiffLine(DiffLineType.ADD, line.removePrefix("+"), newLineNumber = newLine))
                    newLine++
                }

                // Removed line
                line.startsWith("-") -> {
                    currentHunk?.addLine(DiffLine(DiffLineType.REMOVE, line.removePrefix("-"), oldLineNumber = oldLine))
                    oldLine++
                }

                // Context line
                line.startsWith(" ") || line == "" -> {
                    val content = if (line.startsWith(" ")) line.removePrefix(" ") else ""
                    currentHunk?.addLine(DiffLine(DiffLineType.CONTEXT, content, oldLineNumber = oldLine, newLineNumber = newLine))
                    oldLine++
                    newLine++
                }

                // No newline marker
                line.startsWith("\\") && line.contains("No newline") -> {
                    currentHunk?.addLine(DiffLine(DiffLineType.NO_NEWLINE, line))
                }

                // Binary / mode / index lines
                line.startsWith("Binary files") || line.startsWith("index ") || line.startsWith("mode ") || line.startsWith("new file") || line.startsWith("deleted file") || line.startsWith("similarity") || line.startsWith("rename") -> {
                    if (line.startsWith("Binary files")) {
                        currentFile?.isBinary = true
                    }
                    currentFile?.addLine(DiffLine(DiffLineType.META, line))
                }

                // Fallback: skip unrecognized lines
            }
        }

        // Flush remaining
        currentHunk?.let { currentFile?.addHunk(it) }
        currentFile?.let { files.add(it.build()) }

        val totalInsertions = files.sumOf { it.additions }
        val totalDeletions = files.sumOf { it.deletions }
        return DiffResult(
            files = files,
            stats = DiffStats(filesChanged = files.size, insertions = totalInsertions, deletions = totalDeletions),
        )
    }

    // ── Internal helpers ─────────────────────────────────────────

    private data class FileHeaderPaths(val old: String, val new: String)

    private fun parseFileHeader(line: String): FileHeaderPaths {
        // diff --git a/path/to/foo b/path/to/bar
        // The paths are prefixed with a/ and b/ respectively
        val rest = line.removePrefix("diff --git ")
        val parts = rest.split(" b/")
        val old = if (parts.isNotEmpty()) "a/${parts[0].removePrefix("a/")}" else ""
        val new = if (parts.size > 1) parts[1] else parts.getOrElse(0) { "" }
        return FileHeaderPaths(old, new)
    }

    private data class HunkHeaderResult(
        val header: String,
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
    )

    private fun parseHunkHeader(line: String): HunkHeaderResult {
        // @@ -oldStart[,oldCount] +newStart[,newCount] @@[ optional text]
        val regex = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
        val match = regex.find(line) ?: return HunkHeaderResult(line, 1, 1, 1, 1)

        return HunkHeaderResult(
            header = line,
            oldStart = match.groupValues[1].toIntOrNull() ?: 1,
            oldCount = match.groupValues[2].toIntOrNull() ?: 1,
            newStart = match.groupValues[3].toIntOrNull() ?: 1,
            newCount = match.groupValues[4].toIntOrNull() ?: 1,
        )
    }

    // ── Builder helpers ──────────────────────────────────────────

    private class DiffFileBuilder(
        var oldPath: String,
        var newPath: String,
    ) {
        var isBinary: Boolean = false
        var isDeleted: Boolean = false
        var isNew: Boolean = false
        var isRenamed: Boolean = false
        private val metaLines = mutableListOf<DiffLine>()
        private val hunks = mutableListOf<DiffHunk>()

        fun addLine(line: DiffLine) { metaLines.add(line) }
        fun addHunk(hunk: DiffHunkBuilder) { hunks.add(hunk.build()) }

        fun build(): DiffFile = DiffFile(
            oldPath = oldPath.removePrefix("a/"),
            newPath = newPath.removePrefix("b/"),
            hunks = hunks,
            isBinary = isBinary,
            isDeleted = isDeleted,
            isNew = isNew,
            isRenamed = isRenamed,
        )
    }

    private class DiffHunkBuilder(
        private val header: String,
        private val oldStart: Int,
        private val oldCount: Int,
        private val newStart: Int,
        private val newCount: Int,
    ) {
        private val lines = mutableListOf<DiffLine>()

        fun addLine(line: DiffLine) { lines.add(line) }

        fun build(): DiffHunk = DiffHunk(
            header = header,
            oldStart = oldStart,
            oldCount = oldCount,
            newStart = newStart,
            newCount = newCount,
            lines = lines.toList(),
        )
    }
}