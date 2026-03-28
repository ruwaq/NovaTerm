package com.novaterm.core.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.novaterm.core.common.model.SessionInfo

class TerminalNotificationProvider(
    private val context: Context,
    private val channelId: String,
    private val activityClass: Class<*>,
) {
    fun buildServiceNotification(
        sessions: List<SessionInfo>,
        wakeLockHeld: Boolean,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, activityClass),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val sessionCount = sessions.size
        val runningCount = sessions.count { it.status == com.novaterm.core.common.model.SessionStatus.RUNNING }

        val statusParts = mutableListOf<String>()
        statusParts.add("$runningCount session${if (runningCount != 1) "s" else ""}")
        if (wakeLockHeld) statusParts.add("wake lock held")

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("NovaTerm")
            .setContentText(statusParts.joinToString(" | "))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildAlertNotification(
        title: String,
        message: String,
        notificationId: Int,
        alertChannelId: String,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, activityClass),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, alertChannelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
}
