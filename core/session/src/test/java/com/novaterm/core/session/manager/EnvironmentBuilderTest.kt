package com.novaterm.core.session.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for EnvironmentBuilder logic.
 *
 * Since EnvironmentBuilder requires an Android Context (for dataDir,
 * packageName, EncryptedSharedPreferences), we test the pure logic
 * patterns extracted from buildEnvironment:
 * - Environment variable construction
 * - PATH ordering
 * - XDG directory mapping
 * - LINES/COLUMNS from extraVars
 * - LD_PRELOAD candidate resolution
 * - API key export filtering
 */
class EnvironmentBuilderTest {

    // Simulate buildEnvironment's core logic without Android Context
    private fun buildEnv(
        homeDir: String = "/data/data/com.nvterm/files/home",
        prefix: String = "/data/data/com.nvterm/files/usr",
        rootDir: String = "/data/data/com.nvterm/files",
        shell: String = "/data/data/com.nvterm/files/usr/bin/bash",
        appVersion: String = "0.3.1",
        extraVars: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val env = LinkedHashMap<String, String>()
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["HOME"] = homeDir
        env["PREFIX"] = prefix
        env["LANG"] = "en_US.UTF-8"
        env["PATH"] = "$homeDir/.local/bin:$prefix/bin:$prefix/bin/applets:/system/bin"
        env["TMPDIR"] = "$prefix/tmp"
        env["TMP"] = "$prefix/tmp"
        env["TEMP"] = "$prefix/tmp"
        env["SHELL"] = shell
        env["LD_LIBRARY_PATH"] = "$prefix/lib"
        env["ENV"] = "$homeDir/.shrc"
        env["PWD"] = homeDir
        env["OLDPWD"] = homeDir
        env["SHLVL"] = "1"
        env["USER"] = "shell"
        env["LOGNAME"] = "shell"
        env["XDG_CONFIG_HOME"] = "$homeDir/.config"
        env["XDG_DATA_HOME"] = "$homeDir/.local/share"
        env["XDG_STATE_HOME"] = "$homeDir/.local/state"
        env["XDG_CACHE_HOME"] = "$homeDir/.cache"
        env["XDG_RUNTIME_DIR"] = "$prefix/tmp"
        env["TERM_PROGRAM"] = "novaterm"
        env["TERM_PROGRAM_VERSION"] = appVersion
        env["CLAUDE_CODE_SCROLL_SPEED"] = "3"
        env["AIDER_DARK_MODE"] = "true"
        env["LINES"] = extraVars.getOrDefault("LINES", "40")
        env["COLUMNS"] = extraVars.getOrDefault("COLUMNS", "120")
        env["NOVATERM__ROOTFS"] = rootDir
        env["NOVATERM__PREFIX"] = prefix
        env["TERMUX__ROOTFS"] = rootDir
        env["TERMUX__PREFIX"] = prefix
        env["TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE"] = "enable"
        env.putAll(extraVars)
        return env
    }

    @Test
    fun `TERM is xterm-256color`() {
        val env = buildEnv()
        assertEquals("xterm-256color", env["TERM"])
    }

    @Test
    fun `COLORTERM is truecolor`() {
        val env = buildEnv()
        assertEquals("truecolor", env["COLORTERM"])
    }

    @Test
    fun `PATH has correct order`() {
        val env = buildEnv()
        val path = env["PATH"]!!
        val parts = path.split(":")
        assertEquals(4, parts.size)
        assertTrue(parts[0].endsWith(".local/bin"))
        assertTrue(parts[1].endsWith("/bin"))
        assertTrue(parts[2].endsWith("/bin/applets"))
        assertEquals("/system/bin", parts[3])
    }

    @Test
    fun `XDG directories use home dir`() {
        val home = "/test/home"
        val env = buildEnv(homeDir = home)
        assertEquals("$home/.config", env["XDG_CONFIG_HOME"])
        assertEquals("$home/.local/share", env["XDG_DATA_HOME"])
        assertEquals("$home/.local/state", env["XDG_STATE_HOME"])
        assertEquals("$home/.cache", env["XDG_CACHE_HOME"])
    }

    @Test
    fun `XDG_RUNTIME_DIR uses prefix tmp`() {
        val prefix = "/test/prefix"
        val env = buildEnv(prefix = prefix)
        assertEquals("$prefix/tmp", env["XDG_RUNTIME_DIR"])
    }

    @Test
    fun `TMPDIR TMP TEMP all point to prefix tmp`() {
        val prefix = "/test/prefix"
        val env = buildEnv(prefix = prefix)
        val expected = "$prefix/tmp"
        assertEquals(expected, env["TMPDIR"])
        assertEquals(expected, env["TMP"])
        assertEquals(expected, env["TEMP"])
    }

    @Test
    fun `LINES defaults to 40`() {
        val env = buildEnv()
        assertEquals("40", env["LINES"])
    }

    @Test
    fun `COLUMNS defaults to 120`() {
        val env = buildEnv()
        assertEquals("120", env["COLUMNS"])
    }

    @Test
    fun `extraVars override LINES and COLUMNS`() {
        val env = buildEnv(extraVars = mapOf("LINES" to "50", "COLUMNS" to "200"))
        assertEquals("50", env["LINES"])
        assertEquals("200", env["COLUMNS"])
    }

    @Test
    fun `extraVars can override any variable`() {
        val env = buildEnv(extraVars = mapOf("TERM" to "screen-256color"))
        assertEquals("screen-256color", env["TERM"])
    }

    @Test
    fun `TERM_PROGRAM is novaterm`() {
        val env = buildEnv()
        assertEquals("novaterm", env["TERM_PROGRAM"])
    }

    @Test
    fun `TERM_PROGRAM_VERSION uses appVersion`() {
        val env = buildEnv(appVersion = "2.0.0")
        assertEquals("2.0.0", env["TERM_PROGRAM_VERSION"])
    }

    @Test
    fun `AI tool vars are set`() {
        val env = buildEnv()
        assertEquals("3", env["CLAUDE_CODE_SCROLL_SPEED"])
        assertEquals("true", env["AIDER_DARK_MODE"])
    }

    @Test
    fun `Termux compat vars match NovaTerm vars`() {
        val env = buildEnv()
        assertEquals(env["NOVATERM__ROOTFS"], env["TERMUX__ROOTFS"])
        assertEquals(env["NOVATERM__PREFIX"], env["TERMUX__PREFIX"])
    }

    @Test
    fun `USER and LOGNAME are shell`() {
        val env = buildEnv()
        assertEquals("shell", env["USER"])
        assertEquals("shell", env["LOGNAME"])
    }

    @Test
    fun `SHLVL starts at 1`() {
        val env = buildEnv()
        assertEquals("1", env["SHLVL"])
    }

    @Test
    fun `LD_PRELOAD candidate ordering`() {
        // Test the candidate path generation logic
        val prefix = "/usr"
        val dataDir = "/data/app"
        val legacyDir = "/data/legacy"
        val ldPreloadNames = listOf(
            "libtermux-exec-linker-ld-preload.so",
            "libtermux-exec-ld-preload.so",
            "libtermux-exec.so",
        )
        val candidates = ldPreloadNames.flatMap { name ->
            listOf("$prefix/lib/$name", "$dataDir/files/usr/lib/$name", "$legacyDir/files/usr/lib/$name")
        }
        assertEquals(9, candidates.size)
        // First candidate should be the preferred library in the preferred location
        assertEquals("$prefix/lib/libtermux-exec-linker-ld-preload.so", candidates[0])
        // Last candidate is the least preferred combo
        assertEquals("$legacyDir/files/usr/lib/libtermux-exec.so", candidates[8])
    }

    @Test
    fun `API key export filters empty strings`() {
        // Simulate the export logic
        val env = mutableMapOf<String, String>()
        val keys = mapOf(
            "api_key_anthropic" to "sk-ant-123",
            "api_key_google" to "",        // empty — should be skipped
            "api_key_openai" to null,      // null — should be skipped
            "api_key_openrouter" to "or-key-456",
        )

        keys["api_key_anthropic"]?.takeIf { it.isNotEmpty() }?.let { env["ANTHROPIC_API_KEY"] = it }
        keys["api_key_google"]?.takeIf { it.isNotEmpty() }?.let {
            env["GOOGLE_API_KEY"] = it
            env["GEMINI_API_KEY"] = it
        }
        keys["api_key_openai"]?.takeIf { it.isNotEmpty() }?.let { env["OPENAI_API_KEY"] = it }
        keys["api_key_openrouter"]?.takeIf { it.isNotEmpty() }?.let { env["OPENROUTER_API_KEY"] = it }

        assertEquals("sk-ant-123", env["ANTHROPIC_API_KEY"])
        assertFalse(env.containsKey("GOOGLE_API_KEY"))
        assertFalse(env.containsKey("GEMINI_API_KEY"))
        assertFalse(env.containsKey("OPENAI_API_KEY"))
        assertEquals("or-key-456", env["OPENROUTER_API_KEY"])
    }

    @Test
    fun `Google API key sets both GOOGLE and GEMINI`() {
        val env = mutableMapOf<String, String>()
        val googleKey = "goog-key-789"
        googleKey.takeIf { it.isNotEmpty() }?.let {
            env["GOOGLE_API_KEY"] = it
            env["GEMINI_API_KEY"] = it
        }
        assertEquals(googleKey, env["GOOGLE_API_KEY"])
        assertEquals(googleKey, env["GEMINI_API_KEY"])
    }

    @Test
    fun `env output format is KEY=VALUE`() {
        val env = buildEnv()
        val array = env.map { "${it.key}=${it.value}" }.toTypedArray()
        assertTrue(array.all { it.contains("=") })
        assertTrue(array.any { it.startsWith("TERM=") })
        assertTrue(array.any { it.startsWith("HOME=") })
    }
}
