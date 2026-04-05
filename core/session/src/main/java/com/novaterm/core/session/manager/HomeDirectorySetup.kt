package com.novaterm.core.session.manager

import android.os.Environment
import android.system.Os
import android.util.Log
import java.io.File

/**
 * Creates the home directory structure and storage symlinks on first run.
 *
 * Handles XDG directory layout, SSH permissions, storage symlinks,
 * and the /tmp symlink for tool compatibility.
 */
class HomeDirectorySetup(
    private val shellConfigWriter: ShellConfigWriter,
) {
    /**
     * Ensure home directory exists and is properly initialized.
     * On first run, creates XDG dirs, storage links, and shell configs.
     *
     * @return the home directory path
     */
    fun ensureHomeDirectory(homeDir: String, prefix: String, rootDir: String): String {
        val home = File(homeDir)
        val tmpdir = File("$prefix/tmp")
        val firstRun = !home.isDirectory
        if (firstRun) home.mkdirs()
        if (!tmpdir.isDirectory) tmpdir.mkdirs()

        // Create /tmp symlink pointing to $PREFIX/tmp.
        // Many tools (Claude Code, npm, Python) hardcode /tmp.
        try {
            val tmpLink = File(rootDir, "tmp")
            if (!tmpLink.exists()) {
                Os.symlink(tmpdir.absolutePath, tmpLink.absolutePath)
                Log.i("NovaTerm", "Created /tmp symlink → ${tmpdir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w("NovaTerm", "Could not create /tmp symlink (non-fatal)", e)
        }

        if (firstRun) {
            setupDirectoryStructure(home)
            setupStorageLinks(home)
            shellConfigWriter.writeConfigs(home)
        }

        return homeDir
    }

    private fun setupDirectoryStructure(home: File) {
        File(home, "projects").mkdirs()

        // XDG directories (freedesktop.org Base Directory Specification)
        File(home, ".config").mkdirs()
        File(home, ".local/bin").mkdirs()
        File(home, ".local/share").mkdirs()
        File(home, ".local/state").mkdirs()
        File(home, ".cache").mkdirs()

        // SSH directory with correct permissions (0700)
        val sshDir = File(home, ".ssh")
        sshDir.mkdirs()
        sshDir.setReadable(false, false)
        sshDir.setReadable(true, true)
        sshDir.setWritable(false, false)
        sshDir.setWritable(true, true)
        sshDir.setExecutable(false, false)
        sshDir.setExecutable(true, true)
    }

    private fun setupStorageLinks(home: File) {
        val storageDir = File(home, "storage")
        if (storageDir.exists()) return
        storageDir.mkdirs()

        val sdcard = Environment.getExternalStorageDirectory().absolutePath

        mapOf(
            "shared" to sdcard,
            "downloads" to "$sdcard/Download",
            "documents" to "$sdcard/Documents",
            "dcim" to "$sdcard/DCIM",
            "pictures" to "$sdcard/Pictures",
            "music" to "$sdcard/Music",
        ).forEach { (name, target) ->
            try {
                val targetFile = File(target)
                if (targetFile.isDirectory) {
                    val link = File(storageDir, name)
                    if (!link.exists()) {
                        Os.symlink(target, link.absolutePath)
                    }
                }
            } catch (_: Exception) {
                // Storage may not be accessible yet
            }
        }
    }
}
