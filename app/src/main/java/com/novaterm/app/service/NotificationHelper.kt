package com.novaterm.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.novaterm.app.NovaTermApp
import com.novaterm.app.R
import com.novaterm.app.ui.MainActivity
import com.novaterm.terminal.TerminalSession

/**
 * Handles all notification and dynamic shortcut logic for [TerminalService].
 *
 * Extracted from TerminalService to separate UI notification concerns from
 * session management. Uses lambdas to read session state without holding a
 * reference to the Service object itself.
 *
 * @param context Application context (Service is a Context).
 * @param sessionsProvider Lambda that returns the current session list.
 * @param isWakeLockHeld Lambda that returns whether the CPU wake lock is held
 *                       (affects notification priority).
 */
internal class NotificationHelper(
    private val context: Context,
    private val sessionsProvider: () -> List<TerminalSession>,
    private val isWakeLockHeld: () -> Boolean,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Notification ID for the persistent foreground-service notification. */
    val foregroundNotificationId: Int = NOTIFICATION_SERVICE_ID

    // ── Foreground service notification ──────────────────────

    /** Build the persistent foreground-service notification. */
    fun buildForegroundNotification(): Notification {
        val sessions = sessionsProvider()
        val count = sessions.size

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val newSessionIntent = PendingIntent.getService(
            context, REQ_NEW_SESSION,
            Intent(context, TerminalService::class.java).setAction(TerminalService.ACTION_NEW_SESSION),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val floatIntent = PendingIntent.getActivity(
            context, REQ_FLOAT,
            Intent(context, MainActivity::class.java)
                .setAction(TerminalService.ACTION_FLOAT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val exitIntent = PendingIntent.getService(
            context, REQ_EXIT,
            Intent(context, TerminalService::class.java).setAction(TerminalService.ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, NovaTermApp.CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.notification_title_running))
            .setContentText(context.getString(R.string.notification_sessions, count))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(
                if (isWakeLockHeld()) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_LOW
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_input_add,
                context.getString(R.string.action_new_session),
                newSessionIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_crop,
                context.getString(R.string.action_float),
                floatIntent,
            )
            .addAction(
                android.R.drawable.ic_delete,
                context.getString(R.string.action_exit),
                exitIntent,
            )
            .build()
    }

    private val updateScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Debounced foreground notification update.
     * Batches rapid changes (session create/close) into a single rebuild.
     */
    fun scheduleUpdate() {
        if (!updateScheduled.compareAndSet(false, true)) return
        mainHandler.postDelayed({
            updateScheduled.set(false)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_SERVICE_ID, buildForegroundNotification())
            updateDynamicShortcuts()
        }, NOTIFICATION_DEBOUNCE_MS)
    }

    // ── Alert notifications ───────────────────────────────────

    fun sendBellNotification(session: TerminalSession) {
        val title = session.title?.takeIf { it.isNotBlank() } ?: "Terminal"
        val intent = PendingIntent.getActivity(
            context, NOTIFICATION_BELL,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NovaTermApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_bell_activity))
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_BELL, notification)
    }

    fun sendCommandCompletionNotification(
        session: TerminalSession,
        command: String,
        exitCode: Int?,
    ) {
        val code = exitCode ?: 0
        val title = if (code == 0) context.getString(R.string.notification_command_done)
        else context.getString(R.string.notification_command_failed, code)

        val displayCommand = if (command.length > 120) command.take(117) + "…" else command
        val sessionIndex = sessionsProvider().indexOf(session)
        if (sessionIndex < 0) return  // Session removed — skip notification
        val notificationId = NOTIFICATION_COMMAND_DONE + sessionIndex

        val intent = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java).apply {
                putExtra("session_index", sessionIndex)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NovaTermApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(displayCommand)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayCommand))
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }

    fun sendOsc9Notification(session: TerminalSession, text: String) {
        val sessionTitle = session.title?.takeIf { it.isNotBlank() } ?: "Terminal"
        val sessionIndex = sessionsProvider().indexOf(session)
        if (sessionIndex < 0) return  // Session removed — skip notification
        val notificationId = NOTIFICATION_OSC9 + sessionIndex

        val intent = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java).apply {
                putExtra("session_index", sessionIndex)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NovaTermApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sessionTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }

    // ── Dynamic shortcuts ─────────────────────────────────────

    private fun updateDynamicShortcuts() {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val sessions = sessionsProvider()
        if (sessions.isEmpty()) {
            shortcutManager.removeAllDynamicShortcuts()
            return
        }
        val shortcuts = sessions.take(MAX_SHORTCUTS).mapIndexed { index, session ->
            @Suppress("StringFormatMatches")
            val label = session.title?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.shortcut_session, index + 1)
            val intent = Intent(context, MainActivity::class.java).apply {
                action = TerminalService.ACTION_SWITCH_SESSION
                putExtra("session_index", index)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ShortcutInfo.Builder(context, "session_$index")
                .setShortLabel(label ?: "")
                .setLongLabel(label ?: "")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_notification))
                .setIntent(intent)
                .setRank(index)
                .build()
        }
        shortcutManager.dynamicShortcuts = shortcuts
    }

    companion object {
        private const val NOTIFICATION_SERVICE_ID = 1337
        private const val NOTIFICATION_BELL = 1338
        private const val NOTIFICATION_OSC9 = 1340
        private const val NOTIFICATION_COMMAND_DONE = 1350
        private const val REQ_EXIT = 1
        private const val REQ_NEW_SESSION = 2
        private const val REQ_FLOAT = 4
        private const val MAX_SHORTCUTS = 4
        /** Debounce delay (ms) to batch rapid session changes into one notification rebuild. */
        private const val NOTIFICATION_DEBOUNCE_MS = 300L
    }
}
