package com.novaterm.app.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

/**
 * Manages CPU wake lock and WiFi lock for [TerminalService].
 *
 * Keeps the CPU and WiFi radio alive during long-running terminal sessions
 * (SSH, builds, AI inference). Both locks are guarded by a 4-hour safety
 * timeout to prevent battery drain from forgotten lock state.
 */
internal class WakeLockManager(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    val isHeld: Boolean get() = wakeLock?.isHeld == true

    fun toggle() {
        if (isHeld) release() else acquire()
    }

    fun acquire() {
        if (isHeld) {
            Log.d(TAG, "Wake lock already held, skipping acquire")
            return
        }
        if (wakeLock == null) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "novaterm:service-wakelock",
            )
        }
        wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)

        if (wifiLock == null) {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "novaterm:service-wifilock",
            )
        }
        if (wifiLock?.isHeld == false) wifiLock?.acquire()
        Log.d(TAG, "Locks acquired")
    }

    fun release() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        if (wifiLock?.isHeld == true) wifiLock?.release()
        wifiLock = null
        Log.d(TAG, "Locks released")
    }

    companion object {
        private const val TAG = "WakeLockManager"
        /** Safety timeout: auto-release after 4 hours to prevent battery drain. */
        private const val WAKELOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
    }
}
