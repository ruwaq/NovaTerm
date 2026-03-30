package com.novaterm.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    // ── Suggestion prompt ───────────────────────────────────

    @Test
    fun `suggestion prompt includes CWD when present`() {
        val context = TerminalContext(cwd = "/home/user/projects")
        val prompt = PromptBuilder.buildSuggestionPrompt(context)

        assertTrue(prompt.contains("CWD: /home/user/projects"))
    }

    @Test
    fun `suggestion prompt includes recent commands`() {
        val context = TerminalContext(
            recentCommands = listOf(
                CommandEntry("git add .", exitCode = 0),
                CommandEntry("git status", exitCode = 0),
            ),
        )
        val prompt = PromptBuilder.buildSuggestionPrompt(context)

        assertTrue(prompt.contains("$ git add . [exit=0]"))
        assertTrue(prompt.contains("$ git status [exit=0]"))
    }

    @Test
    fun `suggestion prompt limits history depth`() {
        val commands = (1..20).map { CommandEntry("cmd_$it") }
        val context = TerminalContext(recentCommands = commands)
        val prompt = PromptBuilder.buildSuggestionPrompt(context)

        // Should only include last 8
        assertTrue(prompt.contains("cmd_20"))
        assertTrue(prompt.contains("cmd_13"))
        assertTrue(!prompt.contains("cmd_1\n"))
    }

    @Test
    fun `suggestion prompt includes last output`() {
        val context = TerminalContext(
            lastOutput = "Error: file not found",
        )
        val prompt = PromptBuilder.buildSuggestionPrompt(context)

        assertTrue(prompt.contains("Error: file not found"))
    }

    @Test
    fun `suggestion prompt includes current prefix`() {
        val context = TerminalContext(currentPrefix = "git co")
        val prompt = PromptBuilder.buildSuggestionPrompt(context)

        assertTrue(prompt.contains("User is typing: git co"))
    }

    @Test
    fun `suggestion prompt follows Gemma format`() {
        val context = TerminalContext()
        val prompt = PromptBuilder.buildSuggestionPrompt(context)

        assertTrue(prompt.startsWith("<start_of_turn>user\n"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
        assertTrue(prompt.contains("<end_of_turn>"))
    }

    @Test
    fun `suggestion prompt works with empty context`() {
        val context = TerminalContext()
        val prompt = PromptBuilder.buildSuggestionPrompt(context)

        assertNotNull(prompt)
        assertTrue(prompt.isNotBlank())
        assertTrue(prompt.contains("Suggest the next shell command"))
    }

    // ── Explanation prompt ──────────────────────────────────

    @Test
    fun `explanation prompt includes input`() {
        val prompt = PromptBuilder.buildExplanationPrompt(
            "chmod 755 /usr/bin/script",
            TerminalContext(),
        )
        assertTrue(prompt.contains("chmod 755 /usr/bin/script"))
    }

    @Test
    fun `explanation prompt follows Gemma format`() {
        val prompt = PromptBuilder.buildExplanationPrompt("ls", TerminalContext())

        assertTrue(prompt.startsWith("<start_of_turn>user\n"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    // ── Response parsing ────────────────────────────────────

    @Test
    fun `parseCommandResponse extracts clean command`() {
        assertEquals("git push origin main", PromptBuilder.parseCommandResponse("git push origin main"))
    }

    @Test
    fun `parseCommandResponse strips markdown code blocks`() {
        assertEquals("ls -la", PromptBuilder.parseCommandResponse("```bash\nls -la\n```"))
        assertEquals("ls -la", PromptBuilder.parseCommandResponse("```\nls -la\n```"))
    }

    @Test
    fun `parseCommandResponse strips dollar prefix`() {
        assertEquals("git status", PromptBuilder.parseCommandResponse("$ git status"))
    }

    @Test
    fun `parseCommandResponse takes first non-comment line`() {
        val response = """
            # This is a comment
            git commit -m 'fix'
            # Another comment
        """.trimIndent()
        assertEquals("git commit -m 'fix'", PromptBuilder.parseCommandResponse(response))
    }

    @Test
    fun `parseCommandResponse rejects empty response`() {
        assertNull(PromptBuilder.parseCommandResponse(""))
        assertNull(PromptBuilder.parseCommandResponse("   "))
    }

    @Test
    fun `parseCommandResponse rejects overly long response`() {
        val long = "a".repeat(501)
        assertNull(PromptBuilder.parseCommandResponse(long))
    }

    @Test
    fun `parseCommandResponse handles whitespace-only lines`() {
        assertNull(PromptBuilder.parseCommandResponse("   \n  \n  "))
    }

    @Test
    fun `parseCommandResponse handles mixed formatting`() {
        assertEquals(
            "npm install",
            PromptBuilder.parseCommandResponse("```sh\n$ npm install\n```"),
        )
    }
}
