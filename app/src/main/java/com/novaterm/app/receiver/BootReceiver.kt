package com.novaterm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op: starting a terminal service at boot with no sessions
        // creates a zombie foreground service.
        // TODO: Re-enable after session persistence is implemented.
    }
}
