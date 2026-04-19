package com.novaterm.app.receiver

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.novaterm.app.NovaTermApp
import com.novaterm.app.R
import com.novaterm.app.service.TerminalService
import com.novaterm.app.ui.MainActivity
import com.novaterm.core.session.persistence.SessionStore

/**
 * Starts TerminalService on device boot if there are saved sessions to restore.
 * Only runs if the user had active sessions when the device was last shut down.
 *
 * Disabled by default in manifest (android:enabled="false").
 * Gets enabled when sessions are active and wake lock is held.
 *
 * Handles Android 12+ foreground service start restrictions:
 * - [ForegroundServiceStartNotAllowedException] when app is restricted
 * - Battery optimization / doze mode blocking service start
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val sessionStore = SessionStore(context)
        val savedSessions = sessionStore.load()
        if (savedSessions.isEmpty()) return

        Log.i(TAG, "Boot: restoring ${savedSessions.size} saved session(s)")

        try {
            context.startForegroundService(Intent(context, TerminalService::class.java))
        } catch (e: Exception) {
            val reason = classifyBootError(e)
            Log.e(TAG, "Boot service start failed: $reason", e)
            notifyBootFailure(context, reason)
        }
    }

    /**
     * Classify the boot error for better user messaging.
     */
    private fun classifyBootError(e: Exception): String {
        return when {
            // Android 12+: app not allowed to start foreground service from background
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException ->
                "Foreground service blocked by system. Disable battery optimization for NovaTerm."

            // SecurityException: missing permission or restricted by OEM
            e is SecurityException ->
                "Permission denied. Check autostart settings for NovaTerm."

            // Generic
            else -> "Unexpected error: ${e.message}"
        }
    }

    private fun notifyBootFailure(context: Context, reason: String) {
        try {
            val contentIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, NovaTermApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("NovaTerm")
                .setContentText(context.getString(R.string.boot_restore_failed))
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as? android.app.NotificationManager ?: return
            manager.notify(BOOT_FAILURE_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show boot failure notification", e)
        }
    }

    companion object {
        private const val TAG = "NovaTerm"
        private const val BOOT_FAILURE_ID = 1339
    }
}
