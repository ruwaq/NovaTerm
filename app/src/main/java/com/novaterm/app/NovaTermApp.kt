package com.novaterm.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class NovaTermApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
        const val CHANNEL_SERVICE = "novaterm_service"
        const val CHANNEL_ALERTS = "novaterm_alerts"
    }
}
