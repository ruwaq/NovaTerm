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

            // 0. Check available storage (bootstrap needs ~100MB)
            val availableBytes = File(filesDir).freeSpace
            val requiredBytes = 100L * 1024 * 1024 // 100MB
            if (availableBytes < requiredBytes) {
                val availableMB = availableBytes / 1024 / 1024
                Log.e(TAG, "Insufficient storage: ${availableMB}MB available, need 100MB")
                _state.value = State.Error("Insufficient storage. Need at least 100MB free (${availableMB}MB available).")
                return@withContext false
            }

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

            // 6. Create symlinks with patched targets (com.termux → com.nvterm in paths)
            val patchedSymlinks = symlinks.map { (target, link) ->
                target.replace("com.termux", "com.nvterm") to link
            }
            createSymlinks(patchedSymlinks, prefixDir)

            // 7. Validate critical files exist (after symlinks, so sh→bash resolves)
            val criticalBinaries = listOf("bin/bash", "bin/sh", "bin/login", "bin/apt", "bin/dpkg")
            val missing = criticalBinaries.filter { !File(prefixDir, it).exists() }
            if (missing.isNotEmpty()) {
                throw RuntimeException("Bootstrap extraction incomplete — missing: ${missing.joinToString()}")
            }

            // 8. Ensure required directories exist (apt, dpkg, cache)
            val requiredDirs = listOf(
                "tmp",
                "etc/apt/apt.conf.d",
                "etc/apt/preferences.d",
                "etc/apt/sources.list.d",
                "etc/apt/trusted.gpg.d",
                "var/lib/apt/lists/partial",
                "var/lib/dpkg/info",
                "var/lib/dpkg/triggers",
                "var/lib/dpkg/updates",
                "var/log/apt",
            )
            for (dir in requiredDirs) File(prefixDir, dir).mkdirs()

            // Also create apt cache outside $PREFIX (where libapt-pkg expects it)
            val cacheDir = File(context.cacheDir, "apt/archives/partial")
            cacheDir.mkdirs()

            // 10. Ensure dpkg status files exist
            File(prefixDir, "var/lib/dpkg/available").apply { if (!exists()) createNewFile() }

            // 11. Install dpkg wrapper that patches .deb paths on install.
            //     Termux .debs have /data/data/com.termux/ hardcoded in data.tar.
            //     Since com.nvterm == com.termux in length, sed binary patch works.
            installDpkgWrapper(prefixDir)

            // 12. Copy GPG keys to trusted.gpg.d if they exist in share/
            val keyringDir = File(prefixDir, "share/termux-keyring")
            val trustedDir = File(prefixDir, "etc/apt/trusted.gpg.d")
            if (keyringDir.isDirectory) {
                keyringDir.listFiles()?.filter { it.extension == "gpg" }?.forEach { key ->
                    val dest = File(trustedDir, key.name)
                    if (!dest.exists()) key.copyTo(dest)
                }
                Log.i(TAG, "Installed ${trustedDir.listFiles()?.size ?: 0} GPG keys")
            }

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
                        // Security: block path traversal
                        val canonical = outFile.canonicalPath
                        val targetCanonical = targetDir.canonicalPath + File.separator
                        if (!canonical.startsWith(targetCanonical)) {
                            Log.w(TAG, "Skipping path traversal entry: $name")
                            entry = zis.nextEntry
                            continue
                        }
                        outFile.parentFile?.mkdirs()

                        // Read into memory, patch com.termux→com.nvterm, write out.
                        // Same-length replacement (10 bytes each) — safe for ALL files
                        // including ELF binaries. Zero extra I/O passes.
                        val data = zis.readBytes()
                        val ext = outFile.extension.lowercase()
                        val shouldPatch = ext !in SKIP_PATCH_EXTENSIONS
                            && name !in SKIP_PATCH_FILES
                        if (shouldPatch) {
                            patchBytes(data, OLD_PKG_BYTES, NEW_PKG_BYTES)
                        }
                        outFile.writeBytes(data)
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

    // ── dpkg wrapper ──────────────────────────────────────

    /**
     * Install a dpkg wrapper that patches downloaded .deb files before
     * dpkg processes them. Termux repo .debs have paths with com.termux
     * hardcoded; we replace with com.nvterm (same length = safe binary sed).
     *
     * Strategy: rename real dpkg to dpkg.real, install a shell script as
     * dpkg that patches any .deb arguments, then calls dpkg.real.
     */
    private fun installDpkgWrapper(prefix: File) {
        val dpkgReal = File(prefix, "bin/dpkg.real")
        val dpkgBin = File(prefix, "bin/dpkg")

        // Only install once
        if (dpkgReal.exists()) return
        if (!dpkgBin.exists()) return

        // Rename real dpkg
        dpkgBin.renameTo(dpkgReal)

        // Write wrapper script
        dpkgBin.writeText(buildString {
            appendLine("#!/data/data/com.nvterm/files/usr/bin/sh")
            appendLine("# NovaTerm dpkg wrapper — patches com.termux paths in .deb files")
            appendLine("# Real dpkg is at dpkg.real")
            appendLine()
            appendLine("DPKG_DEB=\"/data/data/com.nvterm/files/usr/bin/dpkg-deb\"")
            appendLine("patch_deb() {")
            appendLine("  local deb=\"\$1\"")
            appendLine("  [ -f \"\$deb\" ] || return 1")
            appendLine("  local tmp=\"\$(mktemp -d)\"")
            appendLine("  # Extract .deb using dpkg-deb (ar is not in bootstrap)")
            appendLine("  \$DPKG_DEB --raw-extract \"\$deb\" \"\$tmp/pkg\" 2>/dev/null || {")
            appendLine("    rm -rf \"\$tmp\"; return 1")
            appendLine("  }")
            appendLine("  # Rename directory: com.termux → com.nvterm")
            appendLine("  if [ -d \"\$tmp/pkg/data/data/com.termux\" ]; then")
            appendLine("    mkdir -p \"\$tmp/pkg/data/data/com.nvterm\"")
            appendLine("    cp -a \"\$tmp/pkg/data/data/com.termux/.\" \"\$tmp/pkg/data/data/com.nvterm/\"")
            appendLine("    rm -rf \"\$tmp/pkg/data/data/com.termux\"")
            appendLine("  fi")
            appendLine("  # Binary patch file contents (same-length, safe)")
            appendLine("  find \"\$tmp/pkg\" -type f | while IFS= read -r f; do")
            appendLine("    LC_ALL=C sed -i 's/com\\.termux/com.nvterm/g' \"\$f\" 2>/dev/null || true")
            appendLine("  done")
            appendLine("  # Rebuild .deb")
            appendLine("  \$DPKG_DEB -b \"\$tmp/pkg\" \"\$deb\" 2>/dev/null")
            appendLine("  rm -rf \"\$tmp\"")
            appendLine("}")
            appendLine()
            appendLine("# Patch any .deb file arguments before passing to real dpkg")
            appendLine("for arg in \"\$@\"; do")
            appendLine("  case \"\$arg\" in")
            appendLine("    *.deb)")
            appendLine("      [ -f \"\$arg\" ] && patch_deb \"\$arg\"")
            appendLine("      ;;")
            appendLine("  esac")
            appendLine("done")
            appendLine()
            appendLine("# Pass all original args through (POSIX-compatible, no bash arrays)")
            appendLine("exec /data/data/com.nvterm/files/usr/bin/dpkg.real \"\$@\"")
        })

        dpkgBin.setExecutable(true, true)
        dpkgBin.setReadable(true, true)
        Log.i(TAG, "Installed dpkg wrapper script")
    }

    // ── Inline byte patching (during extraction) ───────────

    /**
     * Replace all occurrences of [old] with [new] in [data] in-place.
     * Both arrays MUST have equal length (same-length binary patching).
     */
    private fun patchBytes(data: ByteArray, old: ByteArray, new: ByteArray) {
        var i = 0
        while (i <= data.size - old.size) {
            var match = true
            for (j in old.indices) {
                if (data[i + j] != old[j]) { match = false; break }
            }
            if (match) {
                System.arraycopy(new, 0, data, i, new.size)
                i += new.size
            } else {
                i++
            }
        }
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
        // Critical symlinks that must succeed for the terminal to work
        val criticalLinks = setOf("bin/sh", "bin/bash", "bin/login")
        var created = 0
        var failed = 0
        val criticalFailures = mutableListOf<String>()

        for ((target, linkPath) in symlinks) {
            try {
                val linkFile = File(prefix, linkPath)
                linkFile.parentFile?.mkdirs()
                linkFile.delete()
                Os.symlink(target, linkFile.absolutePath)
                created++
            } catch (e: Exception) {
                Log.w(TAG, "Symlink failed: $linkPath → $target: ${e.message}")
                failed++
                if (linkPath in criticalLinks) {
                    criticalFailures.add(linkPath)
                }
            }
        }
        Log.i(TAG, "Symlinks: $created created, $failed failed")
        if (criticalFailures.isNotEmpty()) {
            Log.e(TAG, "Critical symlinks failed: ${criticalFailures.joinToString()}")
        }
    }

    companion object {
        private const val TAG = "Bootstrap"

        // Same-length byte arrays for inline binary patching during extraction.
        // com.termux (10 bytes) == com.nvterm (10 bytes) — safe for ELF binaries.
        private val OLD_PKG_BYTES = "com.termux".toByteArray(Charsets.US_ASCII)
        private val NEW_PKG_BYTES = "com.nvterm".toByteArray(Charsets.US_ASCII)

        // Extensions containing crypto/compressed data — must NOT be patched.
        private val SKIP_PATCH_EXTENSIONS = setOf(
            "gpg", "pgp", "sig", "asc", "der", "pem", "crt", "key",
            "gz", "xz", "bz2", "zst", "lz4", "zip", "tar", "deb",
            "png", "jpg", "jpeg", "gif", "ico", "webp",
        )

        // Specific files that must NOT be patched (Java class names, etc.)
        private val SKIP_PATCH_FILES = setOf(
            "bin/am",                    // Java class: com.termux.termuxam.Am
            "libexec/termux-am/am.apk",  // APK with Java classes
        )
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
