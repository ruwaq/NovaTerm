package com.novaterm.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.novaterm.app.crash.CrashReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NovaTermApp : Application() {

    val appLifecycle = AppLifecycleObserver()

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        createNotificationChannels()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycle)
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
