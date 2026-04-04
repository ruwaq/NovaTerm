package com.novaterm.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NovaTermApp : Application() {

    val appLifecycle = AppLifecycleObserver()

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        createNotificationChannels()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycle)
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in ${thread.name}", throwable)
            try {
                val crashDir = File(filesDir, "crash-logs")
                crashDir.mkdirs()
                // Keep only last 5 crash logs
                crashDir.listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(4)
                    ?.forEach { it.delete() }
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val crashFile = File(crashDir, "crash_$timestamp.log")
                PrintWriter(crashFile).use { pw ->
                    pw.println("NovaTerm crash report")
                    pw.println("Time: $timestamp")
                    pw.println("Thread: ${thread.name}")
                    pw.println("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    pw.println("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    pw.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    pw.println()
                    throwable.printStackTrace(pw)
                }
                Log.i(TAG, "Crash log saved: ${crashFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_service_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_alerts_desc)
        }

        manager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
    }

    companion object {
        private const val TAG = "NovaTerm"
        const val CHANNEL_SERVICE = "novaterm_service"
        const val CHANNEL_ALERTS = "novaterm_alerts"
    }
}

/**
 * Observes app-level lifecycle to detect foreground/background transitions.
 * Used by [com.novaterm.app.service.TerminalService] to decide whether
 * to send Android notifications (background) or just vibrate (foreground).
 */
class AppLifecycleObserver : DefaultLifecycleObserver {
    private val _isInForeground = MutableStateFlow(true)
    val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        _isInForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isInForeground.value = false
    }
}
