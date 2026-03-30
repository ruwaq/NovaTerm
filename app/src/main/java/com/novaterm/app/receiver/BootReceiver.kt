package com.novaterm.app.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val sessionStore = SessionStore(context)
        if (sessionStore.hasSavedSessions()) {
            Log.i(TAG, "Boot: restoring ${sessionStore.load().size} saved session(s)")
            try {
                context.startForegroundService(Intent(context, TerminalService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service on boot", e)
                notifyBootFailure(context)
            }
        }
    }

    private fun notifyBootFailure(context: Context) {
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
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
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
