package com.novaterm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
            Log.i("NovaTerm", "Boot: restoring ${sessionStore.load().size} saved session(s)")
            try {
                val serviceIntent = Intent(context, Class.forName("com.novaterm.app.service.TerminalService"))
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e("NovaTerm", "Failed to start service on boot", e)
            }
        }
    }
}
