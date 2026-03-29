package com.novaterm.core.bootstrap

import android.content.Context
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Installs the terminal bootstrap environment on first launch.
 *
 * The bootstrap is a ZIP archive (~30MB) containing bash, coreutils, apt,
 * termux-exec, and other essential Unix tools compiled for Android arm64.
 * It is embedded in the APK as a native library (libtermux-bootstrap.so)
 * via the .incbin assembly directive.
 *
 * Installation process:
 * 1. Load ZIP bytes from the native library
 * 2. Extract to a staging directory (usr-staging/)
 * 3. Parse SYMLINKS.txt and create symlinks
 * 4. Set executable permissions on binaries
 * 5. Atomic rename: staging → prefix (prevents partial installs)
 *
 * Architecture inspired by Termux's TermuxInstaller.java (GPLv3),
 * but rewritten from scratch in Kotlin for NovaTerm.
 */
class BootstrapInstaller(private val context: Context) {

    sealed class State {
        data object Idle : State()
        data class Extracting(val progress: Float, val currentFile: String) : State()
        data object Finalizing : State()
        data object Done : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val filesDir: String get() = context.filesDir.absolutePath

    /** The prefix directory where packages are installed ($PREFIX). */
    val prefixDir: File get() = File(filesDir, "usr")

    /** Whether the bootstrap has been installed (bash or sh exists). */
    // Use exists() NOT canExecute() — W^X makes canExecute() return false
    val isBootstrapped: Boolean
        get() = File(prefixDir, "bin/bash").exists() ||
                File(prefixDir, "bin/sh").exists()

    /**
     * Install the bootstrap if not already present. Safe to call multiple times.
     * Returns true if bootstrap is ready (already installed or just installed).
     */
    suspend fun installIfNeeded(): Boolean {
        if (isBootstrapped) {
            _state.value = State.Done
            return true
        }
        return install()
    }

    /**
     * Force (re)installation of the bootstrap.
     */
    suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = State.Extracting(0f, "Loading...")

            // 1. Load ZIP from assets (Phase 1) or native library (Phase 2)
            val zipBytes = try {
                // Try assets first (simpler, no NDK required)
                context.assets.open("bootstrap-aarch64.zip").use { it.readBytes() }
            } catch (_: Exception) {
                try {
                    // Fallback to JNI native library (Phase 2, requires NDK build)
                    NativeBootstrap.getZip()
                } catch (_: UnsatisfiedLinkError) {
                    Log.w(TAG, "No bootstrap found in assets or native library")
                    _state.value = State.Error("Bootstrap not available.")
                    return@withContext false
                }
            }

            Log.i(TAG, "Bootstrap ZIP loaded: ${zipBytes.size} bytes (${zipBytes.size / 1024 / 1024} MB)")

            // 2. Clean staging directory
            val stagingDir = File(filesDir, "usr-staging")
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            // 3. Extract ZIP
            val symlinks = mutableListOf<Pair<String, String>>()
            extractZip(zipBytes, stagingDir, symlinks)

            // 4. Set permissions
            _state.value = State.Finalizing
            setExecutePermissions(stagingDir)

            // 5. Atomic swap: staging → prefix
            val oldPrefix = File(filesDir, "usr-old")
            oldPrefix.deleteRecursively()
            if (prefixDir.exists()) {
                prefixDir.renameTo(oldPrefix)
            }
            if (!stagingDir.renameTo(prefixDir)) {
                // Restore old prefix on failure
                oldPrefix.renameTo(prefixDir)
                throw RuntimeException("Failed to rename staging directory to prefix")
            }
            oldPrefix.deleteRecursively()

            // 6. Create symlinks (after rename, so paths resolve correctly)
            createSymlinks(symlinks, prefixDir)

            // 7. Patch scripts: replace com.termux paths with our package paths
            patchTermuxPaths(prefixDir)

            // 8. Ensure tmp directory exists
            File(prefixDir, "tmp").mkdirs()

            _state.value = State.Done
            Log.i(TAG, "Bootstrap installation complete: ${prefixDir.absolutePath}")
            // Diagnostic: verify critical files exist
            val criticalFiles = listOf(
                "bin/bash", "bin/sh", "bin/login",
                "lib/libtermux-exec-linker-ld-preload.so",
                "lib/libtermux-exec-ld-preload.so",
                "lib/libtermux-exec.so",
            )
            for (f in criticalFiles) {
                val file = File(prefixDir, f)
                Log.i(TAG, "  $f: exists=${file.exists()} size=${if (file.exists()) file.length() else -1}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap installation failed", e)
            _state.value = State.Error(e.message ?: "Installation failed")
            // Clean up staging on failure
            File(filesDir, "usr-staging").deleteRecursively()
            false
        }
    }

    // ── ZIP extraction ────────────────────────────────────

    private fun extractZip(
        zipBytes: ByteArray,
        targetDir: File,
        symlinks: MutableList<Pair<String, String>>,
    ) {
        // Count entries for progress
        var totalEntries = 0
        ZipInputStream(zipBytes.inputStream()).use { counter ->
            while (counter.nextEntry != null) totalEntries++
        }

        var extracted = 0
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name

                when {
                    name == "SYMLINKS.txt" -> {
                        // Format: target←link (Unicode left arrow as delimiter)
                        val content = zis.readBytes().toString(Charsets.UTF_8)
                        content.lines()
                            .filter { "←" in it }
                            .forEach { line ->
                                val parts = line.split("←", limit = 2)
                                if (parts.size == 2) {
                                    symlinks.add(parts[0].trim() to parts[1].trim())
                                }
                            }
                    }
                    !entry.isDirectory -> {
                        val outFile = File(targetDir, name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos, bufferSize = 64 * 1024)
                        }
                    }
                }

                extracted++
                if (totalEntries > 0) {
                    _state.value = State.Extracting(
                        progress = extracted.toFloat() / totalEntries,
                        currentFile = name.substringAfterLast('/'),
                    )
                }

                entry = zis.nextEntry
            }
        }

        Log.i(TAG, "Extracted $extracted files, found ${symlinks.size} symlinks")
    }

    // ── Permissions ───────────────────────────────────────

    // ── Path patching ──────────────────────────────────

    /**
     * Replace hardcoded com.termux paths in shell scripts with our package paths.
     * The bootstrap is built from Termux packages which have /data/data/com.termux
     * hardcoded. We patch text files (scripts, configs) to use our actual paths.
     * Binary ELF files are NOT patched — termux-exec handles those at runtime.
     */
    private fun patchTermuxPaths(prefix: File) {
        val termuxPrefix = "/data/data/com.termux/files"
        val ourPrefix = context.filesDir.absolutePath

        // Patch shell scripts in bin/ and etc/
        val dirsToPath = listOf("bin", "etc", "etc/profile.d", "etc/apt")
        var patched = 0

        for (dir in dirsToPath) {
            val d = File(prefix, dir)
            if (!d.isDirectory) continue
            d.listFiles()?.forEach { file ->
                if (file.isFile && file.length() < 100_000) { // Only small files (scripts)
                    try {
                        val content = file.readText()
                        if (content.contains(termuxPrefix)) {
                            file.writeText(content.replace(termuxPrefix, ourPrefix))
                            patched++
                        }
                    } catch (_: Exception) {
                        // Skip binary files that throw on readText
                    }
                }
            }
        }

        Log.i(TAG, "Patched $patched files: $termuxPrefix → $ourPrefix")
    }

    // ── Permissions ───────────────────────────────────

    private fun setExecutePermissions(prefix: File) {
        // Directories that contain executable files
        val execDirs = listOf("bin", "libexec", "lib/apt/methods")
        for (dir in execDirs) {
            val d = File(prefix, dir)
            if (!d.isDirectory) continue
            d.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.setExecutable(true, true)  // owner-only
                    file.setReadable(true, true)
                    file.setWritable(true, true)
                }
            }
        }

        // Shared libraries also need execute permission
        prefix.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.")) }
            .forEach { it.setExecutable(true, true) }
    }

    // ── Symlinks ──────────────────────────────────────────

    private fun createSymlinks(symlinks: List<Pair<String, String>>, prefix: File) {
        var created = 0
        var failed = 0
        for ((target, linkPath) in symlinks) {
            try {
                val linkFile = File(prefix, linkPath)
                linkFile.parentFile?.mkdirs()
                linkFile.delete() // Remove existing file/symlink
                Os.symlink(target, linkFile.absolutePath)
                created++
            } catch (e: Exception) {
                Log.w(TAG, "Symlink failed: $linkPath → $target: ${e.message}")
                failed++
            }
        }
        Log.i(TAG, "Symlinks: $created created, $failed failed")
    }

    companion object {
        private const val TAG = "Bootstrap"
    }
}

/**
 * JNI bridge to access the bootstrap ZIP embedded in the native library.
 * The ZIP is included via .incbin assembly directive in novaterm-bootstrap-zip.S.
 */
internal object NativeBootstrap {
    init {
        System.loadLibrary("novaterm-bootstrap")
    }

    @JvmStatic
    external fun getZip(): ByteArray
}
