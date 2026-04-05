package com.novaterm.core.session.manager

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Writes shell configuration files on first run.
 *
 * Reads templates from assets/shell/ and writes them to the home directory.
 * Generates .motd dynamically with device info and app version.
 */
class ShellConfigWriter(
    private val context: Context,
    private val appVersion: String,
) {
    fun writeConfigs(home: File) {
        writeShellAsset("profile.sh", File(home, ".profile"), mapOf("VERSION" to appVersion))
        writeShellAsset("bashrc.sh",  File(home, ".bashrc"))
        writeShellAsset("inputrc",    File(home, ".inputrc"))
        writeShellAsset("shrc.sh",    File(home, ".shrc"))
        writeMotd(home)
    }

    private fun writeMotd(home: File) {
        val motd = File(home, ".motd")
        if (motd.exists()) return

        val device = Build.MODEL
        val androidVer = Build.VERSION.RELEASE
        val novaText = "NovaTerm v$appVersion"
        val deviceText = "$device · Android $androidVer"
        val innerWidth = maxOf(novaText.length, deviceText.length) + 4
        val border = "─".repeat(innerWidth)
        val novaPad = " ".repeat((innerWidth - 2 - novaText.length).coerceAtLeast(0))
        val devicePad = " ".repeat((innerWidth - 2 - deviceText.length).coerceAtLeast(0))
        motd.writeText(buildString {
            appendLine()
            appendLine("\u001b[38;5;208m  ╭${border}╮\u001b[0m")
            appendLine("\u001b[38;5;208m  │\u001b[0m  \u001b[1m$novaText\u001b[0m${novaPad}\u001b[38;5;208m│\u001b[0m")
            appendLine("\u001b[38;5;208m  │\u001b[0m  \u001b[38;5;246m$deviceText\u001b[0m${devicePad}\u001b[38;5;208m│\u001b[0m")
            appendLine("\u001b[38;5;208m  ╰${border}╯\u001b[0m")
            appendLine()
            appendLine("\u001b[38;5;246m  ~/projects/\u001b[0m    \u001b[38;5;242m← your workspace\u001b[0m")
            appendLine("\u001b[38;5;246m  ~/storage/\u001b[0m     \u001b[38;5;242m← phone files\u001b[0m")
            appendLine("\u001b[38;5;246m  ~/.config/\u001b[0m     \u001b[38;5;242m← app configs\u001b[0m")
            appendLine()
        })
    }

    private fun writeShellAsset(assetName: String, dest: File, vars: Map<String, String> = emptyMap()) {
        if (dest.exists()) return
        try {
            var content = context.assets.open("shell/$assetName").bufferedReader().readText()
            for ((key, value) in vars) {
                content = content.replace("{{$key}}", value)
            }
            dest.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write shell asset $assetName", e)
        }
    }

    companion object {
        private const val TAG = "AndroidShellProvider"
    }
}
