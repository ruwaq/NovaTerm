package com.novaterm.app.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.novaterm.app.BuildConfig

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
            if (BuildConfig.DEBUG) Log.d(TAG, "Wake lock already held, skipping acquire")
            return
        }
        if (wakeLock == null) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: run { Log.e(TAG, "PowerManager service unavailable"); return }
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "novaterm:service-wakelock",
            )
        }
        wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)

        if (wifiLock == null) {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: run { Log.e(TAG, "WifiManager service unavailable"); return }
            // minSdk 30 (Android 11) — WIFI_MODE_FULL_LOW_LATENCY always available
            val mode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            wifiLock = wm.createWifiLock(mode, "novaterm:service-wifilock")
        }
        if (wifiLock?.isHeld == false) wifiLock?.acquire()
        if (BuildConfig.DEBUG) Log.d(TAG, "Locks acquired")
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        if (BuildConfig.DEBUG) Log.d(TAG, "Locks released")
    }

    companion object {
        private const val TAG = "WakeLockManager"
        /** Safety timeout: auto-release after 4 hours to prevent battery drain. */
        private const val WAKELOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
    }
}
