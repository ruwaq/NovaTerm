package com.novaterm.feature.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.novaterm.feature.agent.data.DiffParser.DiffLineType

class DiffParserTest {

    // ── Empty / blank input ──────────────────────────────────────

    @Test
    fun `empty input returns empty result`() {
        val result = DiffParser.parse("")
        assertTrue(result.files.isEmpty())
        assertEquals(0, result.stats.filesChanged)
        assertEquals(0, result.stats.insertions)
        assertEquals(0, result.stats.deletions)
    }

    @Test
    fun `blank input returns empty result`() {
        val result = DiffParser.parse("   \n  \n")
        assertTrue(result.files.isEmpty())
    }

    // ── Single file modification ────────────────────────────────

    @Test
    fun `parses single file with additions and deletions`() {
        val diff = """
            diff --git a/src/main.rs b/src/main.rs
            index abc1234..def5678 100644
            --- a/src/main.rs
            +++ b/src/main.rs
            @@ -10,7 +10,7 @@ fn main() {
             // context line
             // another context
            -old line removed
            +new line added
             // more context
             // and more
             // end
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)

        val file = result.files[0]
        assertEquals("src/main.rs", file.oldPath)
        assertEquals("src/main.rs", file.newPath)
        assertFalse(file.isNew)
        assertFalse(file.isDeleted)
        assertFalse(file.isRenamed)
        assertFalse(file.isBinary)

        assertEquals(1, file.hunks.size)
        val hunk = file.hunks[0]
        assertEquals(10, hunk.oldStart)
        assertEquals(7, hunk.oldCount)
        assertEquals(10, hunk.newStart)
        assertEquals(7, hunk.newCount)

        // Verify line types
        val adds = hunk.lines.filter { it.type == DiffLineType.ADD }
        val removes = hunk.lines.filter { it.type == DiffLineType.REMOVE }
        assertEquals(1, adds.size)
        assertEquals(1, removes.size)
        assertEquals("new line added", adds[0].content)
        assertEquals("old line removed", removes[0].content)
    }

    @Test
    fun `stats count insertions and deletions`() {
        val diff = """
            diff --git a/foo.txt b/foo.txt
            --- a/foo.txt
            +++ b/foo.txt
            @@ -1,3 +1,5 @@
             line1
            -line2
            -line3
            +line2a
            +line2b
            +line3a
            +line3b
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.stats.filesChanged)
        assertEquals(4, result.stats.insertions)
        assertEquals(2, result.stats.deletions)
        assertEquals("1 file changed, 4 insertion(+), 2 deletion(-)", result.stats.summary)
    }

    // ── New file ─────────────────────────────────────────────────

    @Test
    fun `parses new file creation`() {
        val diff = """
            diff --git a/new_file.txt b/new_file.txt
            new file mode 100644
            index 0000000..abc1234
            --- /dev/null
            +++ b/new_file.txt
            @@ -0,0 +1,3 @@
            +line 1
            +line 2
            +line 3
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)
        assertTrue(result.files[0].isNew)
        assertFalse(result.files[0].isDeleted)
        assertEquals(3, result.stats.insertions)
        assertEquals(0, result.stats.deletions)
    }

    // ── Deleted file ─────────────────────────────────────────────

    @Test
    fun `parses file deletion`() {
        val diff = """
            diff --git a/old_file.txt b/old_file.txt
            deleted file mode 100644
            index abc1234..0000000
            --- a/old_file.txt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -line 1
            -line 2
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)
        assertTrue(result.files[0].isDeleted)
        assertFalse(result.files[0].isNew)
        assertEquals(2, result.stats.deletions)
        assertEquals(0, result.stats.insertions)
    }

    // ── Multi-hunk ────────────────────────────────────────────────

    @Test
    fun `parses multiple hunks in one file`() {
        val diff = """
            diff --git a/large_file.rs b/large_file.rs
            --- a/large_file.rs
            +++ b/large_file.rs
            @@ -5,3 +5,3 @@
             ctx line 1
            -old line A
            +new line A
             ctx line 2
            @@ -50,3 +50,3 @@
             ctx line 3
            -old line B
            +new line B
             ctx line 4
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)
        assertEquals(2, result.files[0].hunks.size)

        assertEquals(5, result.files[0].hunks[0].oldStart)
        assertEquals(50, result.files[0].hunks[1].oldStart)
        assertEquals(2, result.stats.insertions)
        assertEquals(2, result.stats.deletions)
    }

    // ── Multiple files ───────────────────────────────────────────

    @Test
    fun `parses multiple files in one diff`() {
        val diff = """
            diff --git a/file1.txt b/file1.txt
            --- a/file1.txt
            +++ b/file1.txt
            @@ -1 +1 @@
            -old1
            +new1
            diff --git a/file2.txt b/file2.txt
            --- a/file2.txt
            +++ b/file2.txt
            @@ -1 +1 @@
            -old2
            +new2
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(2, result.files.size)
        assertEquals(2, result.stats.filesChanged)
        assertEquals(2, result.stats.insertions)
        assertEquals(2, result.stats.deletions)
    }

    // ── Binary file ──────────────────────────────────────────────

    @Test
    fun `parses binary file marker`() {
        val diff = """
            diff --git a/image.png b/image.png
            Binary files /dev/null and b/image.png differ
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)
        assertTrue(result.files[0].isBinary)
    }

    // ── Rename ───────────────────────────────────────────────────

    @Test
    fun `parses file rename`() {
        val diff = """
            diff --git a/old_name.txt b/new_name.txt
            similarity index 95%
            rename from old_name.txt
            rename to new_name.txt
            --- a/old_name.txt
            +++ b/new_name.txt
            @@ -1 +1 @@
            -old content
            +new content
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)
        val file = result.files[0]
        assertTrue(file.isRenamed)
        assertEquals("old_name.txt → new_name.txt", file.displayPath)
    }

    // ── No newline at end of file ────────────────────────────────

    @Test
    fun `parses no newline marker`() {
        val diff = """
            diff --git a/file.txt b/file.txt
            --- a/file.txt
            +++ b/file.txt
            @@ -1 +1 @@
            -old line
            \ No newline at end of file
            +new line
            \ No newline at end of file
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)
        val noNewline = result.files[0].hunks[0].lines.filter { it.type == DiffLineType.NO_NEWLINE }
        assertEquals(2, noNewline.size)
    }

    // ── Line numbers ─────────────────────────────────────────────

    @Test
    fun `line numbers are tracked correctly`() {
        val diff = """
            diff --git a/num.txt b/num.txt
            --- a/num.txt
            +++ b/num.txt
            @@ -10,5 +10,6 @@
             context1
            -removed1
            +added1
            +added2
             context2
             context3
        """.trimIndent()

        val result = DiffParser.parse(diff)
        val hunk = result.files[0].hunks[0]

        // Context line at old 10, new 10
        val ctx1 = hunk.lines.first { it.type == DiffLineType.CONTEXT && it.content == "context1" }
        assertEquals(10, ctx1.oldLineNumber)
        assertEquals(10, ctx1.newLineNumber)

        // Removed line at old 11
        val rem1 = hunk.lines.first { it.type == DiffLineType.REMOVE }
        assertEquals(11, rem1.oldLineNumber)

        // Added lines at new 11, 12
        val adds = hunk.lines.filter { it.type == DiffLineType.ADD }
        assertEquals(11, adds[0].newLineNumber)
        assertEquals(12, adds[1].newLineNumber)
    }

    // ── displayPath logic ────────────────────────────────────────

    @Test
    fun `displayPath for modified file`() {
        val diff = """
            diff --git a/modified.txt b/modified.txt
            --- a/modified.txt
            +++ b/modified.txt
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals("modified.txt", result.files[0].displayPath)
    }

    // ── Stats summary formatting ─────────────────────────────────

    @Test
    fun `stats summary with zero changes`() {
        val stats = DiffParser.DiffStats(0, 0, 0)
        assertEquals("0 files changed", stats.summary)
    }

    @Test
    fun `stats summary with multiple files`() {
        val stats = DiffParser.DiffStats(3, 10, 5)
        assertEquals("3 files changed, 10 insertion(+), 5 deletion(-)", stats.summary)
    }

    @Test
    fun `stats summary singular file`() {
        val stats = DiffParser.DiffStats(1, 2, 0)
        assertEquals("1 file changed, 2 insertion(+)", stats.summary)
    }
}