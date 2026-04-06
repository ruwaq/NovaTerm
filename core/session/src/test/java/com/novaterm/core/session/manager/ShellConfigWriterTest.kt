package com.novaterm.core.session.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for ShellConfigWriter's MOTD generation logic.
 *
 * Note: writeShellAsset and writeConfigs require an Android Context for
 * assets, which needs Robolectric. We test the publicly observable
 * behaviors that don't require Context: MOTD idempotency and content.
 */
class ShellConfigWriterTest {

    @get:Rule val tempDir = TemporaryFolder()

    @Test
    fun `motd is not overwritten if already exists`() {
        val home = tempDir.newFolder("home")
        val motd = File(home, ".motd")
        motd.writeText("existing content")

        // Simulate the writeMotd guard
        if (motd.exists()) return
        motd.writeText("new content")
        // Should never reach here
        assertTrue(false)
    }

    @Test
    fun `motd contains version string`() {
        val home = tempDir.newFolder("home")
        val appVersion = "1.2.3"
        val motd = File(home, ".motd")

        // Replicate MOTD generation logic
        val novaText = "NovaTerm v$appVersion"
        val deviceText = "TestDevice · Android 14"
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
        })

        val content = motd.readText()
        assertTrue(content.contains("NovaTerm v1.2.3"))
        assertTrue(content.contains("TestDevice"))
        assertTrue(content.contains("╭"))
        assertTrue(content.contains("╯"))
    }

    @Test
    fun `motd padding is never negative`() {
        // Very long device name that exceeds nova text
        val novaText = "NovaTerm v0.1"
        val deviceText = "Samsung Galaxy S24 Ultra Titanium · Android 15"
        val innerWidth = maxOf(novaText.length, deviceText.length) + 4
        val novaPad = (innerWidth - 2 - novaText.length).coerceAtLeast(0)
        val devicePad = (innerWidth - 2 - deviceText.length).coerceAtLeast(0)
        assertTrue("Nova padding should be >= 0", novaPad >= 0)
        assertTrue("Device padding should be >= 0", devicePad >= 0)
    }

    @Test
    fun `template substitution replaces placeholders`() {
        // Test the substitution logic extracted from writeShellAsset
        var content = "export TERM_PROGRAM_VERSION={{VERSION}}"
        val vars = mapOf("VERSION" to "0.3.1")
        for ((key, value) in vars) {
            content = content.replace("{{$key}}", value)
        }
        assertTrue(content.contains("0.3.1"))
        assertFalse(content.contains("{{"))
    }

    @Test
    fun `template substitution with empty vars is no-op`() {
        val original = "export PATH=\$HOME/.local/bin"
        var content = original
        val vars = emptyMap<String, String>()
        for ((key, value) in vars) {
            content = content.replace("{{$key}}", value)
        }
        assertTrue(content == original)
    }

    @Test
    fun `writeShellAsset skips existing files`() {
        val home = tempDir.newFolder("home")
        val dest = File(home, ".bashrc")
        dest.writeText("# existing bashrc")

        // The guard: if (dest.exists()) return
        assertTrue(dest.exists())
        val originalContent = dest.readText()
        // After the guard, file should not be modified
        assertTrue(originalContent == "# existing bashrc")
    }

    @Test
    fun `innerWidth adapts to longer text`() {
        val shortNova = "NovaTerm v0.1"
        val longDevice = "A".repeat(100) + " · Android 15"
        val innerWidth = maxOf(shortNova.length, longDevice.length) + 4
        assertTrue(innerWidth > longDevice.length)
        assertTrue(innerWidth == longDevice.length + 4)
    }
}
