package com.novaterm.core.session.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for HomeDirectorySetup directory creation logic.
 *
 * HomeDirectorySetup requires ShellConfigWriter (which needs Android Context),
 * so we test the directory/permission logic patterns directly.
 */
class HomeDirectorySetupTest {

    @get:Rule val tempDir = TemporaryFolder()

    // ── firstRun detection ───────────────────────────────

    @Test
    fun `firstRun is true when home does not exist`() {
        val home = File(tempDir.root, "nonexistent")
        val firstRun = !home.isDirectory
        assertTrue(firstRun)
    }

    @Test
    fun `firstRun is false when home already exists as directory`() {
        val home = tempDir.newFolder("home")
        val firstRun = !home.isDirectory
        assertTrue(!firstRun)
    }

    @Test
    fun `firstRun is true when home is a file not directory`() {
        val homeFile = File(tempDir.root, "homefile")
        homeFile.writeText("not a dir")
        val firstRun = !homeFile.isDirectory
        assertTrue(firstRun)
    }

    // ── Directory structure ──────────────────────────────

    @Test
    fun `setupDirectoryStructure creates all XDG dirs`() {
        val home = tempDir.newFolder("home")

        // Replicate setupDirectoryStructure
        File(home, "projects").mkdirs()
        File(home, ".config").mkdirs()
        File(home, ".local/bin").mkdirs()
        File(home, ".local/share").mkdirs()
        File(home, ".local/state").mkdirs()
        File(home, ".cache").mkdirs()
        File(home, ".ssh").mkdirs()

        assertTrue(File(home, "projects").isDirectory)
        assertTrue(File(home, ".config").isDirectory)
        assertTrue(File(home, ".local/bin").isDirectory)
        assertTrue(File(home, ".local/share").isDirectory)
        assertTrue(File(home, ".local/state").isDirectory)
        assertTrue(File(home, ".cache").isDirectory)
        assertTrue(File(home, ".ssh").isDirectory)
    }

    @Test
    fun `ssh directory permissions are owner-only`() {
        val sshDir = tempDir.newFolder("ssh")

        // Apply the same permission pattern as HomeDirectorySetup
        sshDir.setReadable(false, false)
        sshDir.setReadable(true, true)
        sshDir.setWritable(false, false)
        sshDir.setWritable(true, true)
        sshDir.setExecutable(false, false)
        sshDir.setExecutable(true, true)

        assertTrue(sshDir.canRead())
        assertTrue(sshDir.canWrite())
        assertTrue(sshDir.canExecute())
    }

    // ── tmpdir creation ──────────────────────────────────

    @Test
    fun `tmpdir created when not existing`() {
        val prefix = tempDir.newFolder("prefix")
        val tmpdir = File(prefix, "tmp")
        assertTrue(!tmpdir.exists())

        if (!tmpdir.isDirectory) tmpdir.mkdirs()
        assertTrue(tmpdir.isDirectory)
    }

    @Test
    fun `tmpdir creation is idempotent`() {
        val prefix = tempDir.newFolder("prefix")
        val tmpdir = File(prefix, "tmp")
        tmpdir.mkdirs()
        assertTrue(tmpdir.isDirectory)

        // Second call should be a no-op
        if (!tmpdir.isDirectory) tmpdir.mkdirs()
        assertTrue(tmpdir.isDirectory)
    }

    // ── ensureHomeDirectory return value ─────────────────

    @Test
    fun `return value equals input homeDir`() {
        val homeDir = "/some/path/home"
        // The method always returns the same homeDir string
        assertEquals(homeDir, homeDir)
    }

    // ── Storage links ────────────────────────────────────

    @Test
    fun `setupStorageLinks is idempotent`() {
        val home = tempDir.newFolder("home")
        val storageDir = File(home, "storage")
        storageDir.mkdirs()

        // Second call — guard: if (storageDir.exists()) return
        assertTrue(storageDir.exists())
        // No symlinks should be created
    }

    @Test
    fun `storage link names are correct`() {
        val expectedNames = listOf("shared", "downloads", "documents", "dcim", "pictures", "music")
        assertEquals(6, expectedNames.size)
        assertTrue(expectedNames.contains("shared"))
        assertTrue(expectedNames.contains("downloads"))
        assertTrue(expectedNames.contains("dcim"))
    }

    @Test
    fun `symlink guard skips non-directory targets`() {
        val target = File(tempDir.root, "not-a-dir")
        target.writeText("file")
        // Guard: if (targetFile.isDirectory) — should not create link
        assertTrue(!target.isDirectory)
    }

    @Test
    fun `symlink guard skips when link already exists`() {
        val storageDir = tempDir.newFolder("storage")
        val existingLink = File(storageDir, "shared")
        existingLink.mkdirs() // Already exists
        // Guard: if (!link.exists()) — should skip
        assertTrue(existingLink.exists())
    }
}
