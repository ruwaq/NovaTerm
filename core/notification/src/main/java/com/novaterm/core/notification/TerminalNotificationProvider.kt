package com.novaterm.core.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.novaterm.core.common.contract.NotificationProvider
import com.novaterm.core.common.contract.ServiceNotification
import com.novaterm.core.common.model.SessionInfo
import com.novaterm.core.common.model.SessionStatus

/**
 * Wraps an Android [Notification] in the platform-agnostic [ServiceNotification] interface.
 */
private class AndroidServiceNotification(
    val notification: Notification,
) : ServiceNotification {
    override val platformNotification: Any get() = notification
}

class TerminalNotificationProvider(
    private val context: Context,
    private val channelId: String,
    private val alertChannelId: String,
    private val activityClass: Class<*>,
    private val smallIconRes: Int = android.R.drawable.ic_menu_manage,
    private val alertIconRes: Int = android.R.drawable.ic_dialog_alert,
) : NotificationProvider {

    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    override fun buildServiceNotification(
        sessions: List<SessionInfo>,
        wakeLockHeld: Boolean,
    ): ServiceNotification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, activityClass),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val runningCount = sessions.count { it.status == SessionStatus.RUNNING }

        val statusParts = mutableListOf<String>()
        statusParts.add("$runningCount session${if (runningCount != 1) "s" else ""}")
        if (wakeLockHeld) statusParts.add("wake lock held")

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("NovaTerm")
            .setContentText(statusParts.joinToString(" | "))
            .setSmallIcon(smallIconRes)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return AndroidServiceNotification(notification)
    }

    override fun showAlert(title: String, message: String) {
        val notificationId = ALERT_BASE_ID + title.hashCode()

        val contentIntent = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, activityClass),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, alertChannelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(alertIconRes)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager?.notify(notificationId, notification)
    }

    override fun dismiss(notificationId: Int) {
        notificationManager?.cancel(notificationId)
    }

    companion object {
        private const val ALERT_BASE_ID = 10_000
    }
}
