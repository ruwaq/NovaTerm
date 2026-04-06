package com.novaterm.app.crash

import android.content.Context
import android.os.Build
import android.util.Log
import com.novaterm.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Privacy-first crash reporter - stores crash logs locally, no external services.
 *
 * On uncaught exception:
 * 1. Captures full stack trace + device info + timestamp
 * 2. Writes to app-private crash_logs/ dir (max [MAX_LOGS] most recent)
 * 3. Calls the original default handler (system crash dialog)
 *
 * On next launch:
 * 1. Check [hasPendingCrash] for presence of crash logs
 * 2. Read latest via [getLatestCrashLog]
 * 3. User can view/share/clear from AboutScreen
 *
 * Storage: [Context.getFilesDir]/crash_logs/crash_*.txt - never leaves the device.
 */
object CrashReporter {

    private const val CRASH_DIR = "crash_logs"
    private const val MAX_LOGS = 5
    private const val TAG = "CrashReporter"
    private val TIMESTAMP_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val DISPLAY_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // --- Installation ---

    /**
     * Installs this reporter as the global uncaught exception handler.
     * Must be called early in [android.app.Application.onCreate].
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(appContext, thread, throwable)
            } catch (e: Throwable) {
                // Never let the reporter itself crash silently
                Log.e(TAG, "Failed to save crash log", e)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        Log.i(TAG, "CrashReporter installed (crash_logs -> ${crashDir(appContext).absolutePath})")
    }

    // --- Write ---

    private fun saveCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashDir(context).also { it.mkdirs() }

        // Rotate: delete oldest files beyond MAX_LOGS - 1 to make room for new one
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS - 1)
            ?.forEach { it.delete() }

        val now = Date()
        val fileName = "crash_${TIMESTAMP_FMT.format(now)}.txt"
        val crashFile = File(dir, fileName)

        val stackTrace = stackTraceOf(throwable)

        crashFile.bufferedWriter().use { writer ->
            writer.appendLine("==========================================")
            writer.appendLine("  NovaTerm Crash Report")
            writer.appendLine("==========================================")
            writer.appendLine()
            writer.appendLine("Timestamp : ${DISPLAY_FMT.format(now)}")
            writer.appendLine("Version   : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            writer.appendLine("Thread    : ${thread.name} (id=${thread.id})")
            writer.appendLine()
            writer.appendLine("-- Device --")
            writer.appendLine("Manufacturer : ${Build.MANUFACTURER}")
            writer.appendLine("Model        : ${Build.MODEL}")
            writer.appendLine("Android      : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            writer.appendLine("ABI          : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
            writer.appendLine()
            writer.appendLine("-- Stack Trace --")
            writer.appendLine(stackTrace)
        }

        Log.i(TAG, "Crash log saved: $fileName")
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    // --- Read ---

    /** Returns true if at least one crash log exists. */
    fun hasPendingCrash(context: Context): Boolean =
        getCrashLogs(context).isNotEmpty()

    /**
     * Returns the content of the most recent crash log,
     * or null if no crash logs exist.
     */
    fun getLatestCrashLog(context: Context): String? =
        getCrashLogs(context).firstOrNull()?.readText()

    /**
     * Returns all crash log files sorted newest-first.
     * Never throws; returns empty list on any I/O error.
     */
    fun getCrashLogs(context: Context): List<File> =
        try {
            crashDir(context)
                .listFiles()
                ?.filter { it.isFile && it.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list crash logs", e)
            emptyList()
        }

    /** Returns the number of stored crash logs. */
    fun getCrashLogCount(context: Context): Int = getCrashLogs(context).size

    // --- Manage ---

    /** Deletes all stored crash logs. */
    fun clearCrashLogs(context: Context) {
        getCrashLogs(context).forEach { it.delete() }
        Log.i(TAG, "All crash logs cleared")
    }

    // --- Internal ---

    private fun crashDir(context: Context): File =
        File(context.filesDir, CRASH_DIR)
}
