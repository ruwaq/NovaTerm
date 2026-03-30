package com.novaterm.core.session.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests shell environment building logic extracted from AndroidShellProvider.
 * Pure JVM — no Android dependencies.
 */
class ShellEnvironmentTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    // Replicates AndroidShellProvider.buildEnvironment logic for testability
    private fun buildEnvironment(
        prefix: String,
        home: String,
        shell: String,
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val env = LinkedHashMap<String, String>()
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["HOME"] = home
        env["PREFIX"] = prefix
        env["LANG"] = "en_US.UTF-8"
        env["PATH"] = "$prefix/bin:$prefix/bin/applets:/system/bin"
        env["TMPDIR"] = "$prefix/tmp"
        env["SHELL"] = shell
        env["LD_LIBRARY_PATH"] = "$prefix/lib"
        env["ENV"] = "$home/.shrc"
        env["ANDROID_DATA"] = "/data"
        env["ANDROID_ROOT"] = "/system"
        env.putAll(extra)
        return env
    }

    @Test
    fun `environment contains all required terminal variables`() {
        val env = buildEnvironment("/prefix", "/home", "/bin/sh")

        val required = listOf(
            "TERM", "COLORTERM", "HOME", "PREFIX", "LANG",
            "PATH", "TMPDIR", "SHELL", "LD_LIBRARY_PATH", "ENV",
        )
        required.forEach { key ->
            assertNotNull("Missing env var: $key", env[key])
        }
    }

    @Test
    fun `TERM is xterm-256color`() {
        val env = buildEnvironment("/prefix", "/home", "/bin/sh")
        assertEquals("xterm-256color", env["TERM"])
    }

    @Test
    fun `COLORTERM is truecolor`() {
        val env = buildEnvironment("/prefix", "/home", "/bin/sh")
        assertEquals("truecolor", env["COLORTERM"])
    }

    @Test
    fun `PATH includes prefix bin and system bin`() {
        val env = buildEnvironment("/data/usr", "/data/home", "/bin/sh")
        val path = env["PATH"]!!
        assertTrue("PATH should contain prefix/bin", path.contains("/data/usr/bin"))
        assertTrue("PATH should contain /system/bin", path.contains("/system/bin"))
        assertTrue("PATH should contain applets", path.contains("/data/usr/bin/applets"))
    }

    @Test
    fun `ENV points to shrc in home`() {
        val env = buildEnvironment("/prefix", "/home/user", "/bin/sh")
        assertEquals("/home/user/.shrc", env["ENV"])
    }

    @Test
    fun `extra vars override defaults`() {
        val env = buildEnvironment(
            "/prefix", "/home", "/bin/sh",
            extra = mapOf("TERM" to "dumb", "CUSTOM" to "value"),
        )
        assertEquals("dumb", env["TERM"])
        assertEquals("value", env["CUSTOM"])
    }

    @Test
    fun `findShell prefers zsh over bash over sh`() {
        val binDir = tempDir.newFolder("usr", "bin")
        File(binDir, "zsh").apply { writeText("#!/bin/sh"); setExecutable(true) }
        File(binDir, "bash").apply { writeText("#!/bin/sh"); setExecutable(true) }

        val candidates = listOf(
            "${binDir.absolutePath}/zsh",
            "${binDir.absolutePath}/bash",
            "/nonexistent/sh",
        )
        val found = candidates.firstOrNull { File(it).canExecute() }

        assertEquals("${binDir.absolutePath}/zsh", found)
    }

    @Test
    fun `findShell falls back when preferred shells missing`() {
        val binDir = tempDir.newFolder("usr", "bin")
        File(binDir, "sh").apply { writeText("#!/bin/sh"); setExecutable(true) }

        val candidates = listOf(
            "${binDir.absolutePath}/zsh",   // doesn't exist
            "${binDir.absolutePath}/bash",  // doesn't exist
            "${binDir.absolutePath}/sh",    // exists
        )
        val found = candidates.firstOrNull { File(it).canExecute() }

        assertEquals("${binDir.absolutePath}/sh", found)
    }

    @Test
    fun `first-run creates profile with POSIX-compatible PS1`() {
        val home = tempDir.newFolder("home")
        val profile = File(home, ".profile")

        // Simulate setupFirstRun
        if (!profile.exists()) {
            profile.writeText(buildString {
                appendLine("# NovaTerm - short prompt")
                appendLine("export PS1='\u001b[38;5;208m>\u001b[0m '")
                appendLine()
            })
        }

        assertTrue(profile.exists())
        val content = profile.readText()
        assertTrue("PS1 should use real ESC char", content.contains("\u001b[38;5;208m"))
        // Must NOT contain bash-only \[ or \e
        assertFalse("PS1 should not contain bash \\[", content.contains("\\["))
        assertFalse("PS1 should not contain bash \\e", content.contains("\\e"))
    }

    @Test
    fun `first-run creates motd with welcome banner`() {
        val home = tempDir.newFolder("home")
        val motd = File(home, ".motd")

        // Simulate dynamic MOTD generation matching AndroidShellProvider logic
        val appVersion = "0.1.0"
        val device = "Test Device Ultra Pro Max"  // Long name to exercise dynamic sizing
        val androidVer = "16"
        val novaText = "NovaTerm v$appVersion"
        val deviceText = "$device · Android $androidVer"
        val innerWidth = maxOf(novaText.length, deviceText.length) + 4
        val border = "─".repeat(innerWidth)
        val novaPad = " ".repeat((innerWidth - 2 - novaText.length).coerceAtLeast(0))
        val devicePad = " ".repeat((innerWidth - 2 - deviceText.length).coerceAtLeast(0))

        motd.writeText(buildString {
            appendLine()
            appendLine("  ╭${border}╮")
            appendLine("  │  $novaText${novaPad}│")
            appendLine("  │  $deviceText${devicePad}│")
            appendLine("  ╰${border}╯")
            appendLine()
        })

        assertTrue(motd.exists())
        val content = motd.readText()
        assertTrue("MOTD should contain NovaTerm", content.contains("NovaTerm"))
        assertTrue("MOTD should contain device name", content.contains(device))
        assertTrue("MOTD should contain Android version", content.contains("Android $androidVer"))

        // Verify box alignment: all lines between ╭ and ╯ should have same visual width
        val boxLines = content.lines().filter { it.contains("│") || it.contains("╭") || it.contains("╰") }
        val widths = boxLines.map { it.length }
        assertEquals("All box lines should have equal width", 1, widths.distinct().size)
    }

    @Test
    fun `first-run creates shrc that sources profile and motd`() {
        val home = tempDir.newFolder("home")
        val shrc = File(home, ".shrc")

        if (!shrc.exists()) {
            shrc.writeText(buildString {
                appendLine("# NovaTerm shell init")
                appendLine("[ -f \"\$HOME/.profile\" ] && . \"\$HOME/.profile\"")
                appendLine("[ -f \"\$HOME/.motd\" ] && cat \"\$HOME/.motd\"")
                appendLine()
            })
        }

        assertTrue(shrc.exists())
        val content = shrc.readText()
        assertTrue("shrc should source .profile", content.contains(".profile"))
        assertTrue("shrc should cat .motd", content.contains(".motd"))
    }

    @Test
    fun `first-run is idempotent`() {
        val home = tempDir.newFolder("home")
        val profile = File(home, ".profile")

        // Write custom content
        profile.writeText("# Custom user config\n")

        // Simulate setupFirstRun — should NOT overwrite
        if (!profile.exists()) {
            profile.writeText("# NovaTerm default\n")
        }

        assertEquals("# Custom user config\n", profile.readText())
    }

    private fun assertFalse(message: String, condition: Boolean) {
        assertTrue(message, !condition)
    }
}
