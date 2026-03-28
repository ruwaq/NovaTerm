package com.novaterm.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novaterm.app.service.TerminalService
import com.novaterm.app.ui.theme.NovaTermTheme
import com.novaterm.app.ui.viewmodel.TerminalViewModel
import com.novaterm.feature.oemcompat.detection.OemDetector

/**
 * Single activity hosting the terminal UI.
 *
 * Service binding is owned by [TerminalViewModel] so it survives
 * configuration changes. The activity only starts the foreground
 * service and delegates binding lifecycle to the ViewModel.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: TerminalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
        requestBatteryExemptionIfNeeded()

        // Start the foreground service (idempotent if already running)
        startForegroundService(
            Intent(this, TerminalService::class.java)
        )

        setContent {
            NovaTermTheme {
                NovaTermApp(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.bindService()
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService()
    }

    /**
     * On aggressive OEMs (Xiaomi, Huawei, Samsung, etc.), request battery
     * optimization exemption once. This is critical for coexistence
     * with other terminal apps — without it, HyperOS/MIUI will kill background
     * apps when NovaTerm takes the foreground.
     *
     * Only shows the dialog once per install. If the user already granted
     * the exemption, [OemDetector.needsBatteryWhitelist] returns false.
     */
    private fun requestBatteryExemptionIfNeeded() {
        val prefs = getSharedPreferences("novaterm_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("battery_exemption_asked", false)) return

        val oemInfo = OemDetector.detect(this)
        Log.i(TAG, "OEM: ${oemInfo.brand.displayName} (aggressiveness=${oemInfo.brand.aggressiveness}), optimized=${oemInfo.isBatteryOptimized}")

        if (OemDetector.needsBatteryWhitelist(oemInfo)) {
            prefs.edit().putBoolean("battery_exemption_asked", true).apply()
            try {
                startActivity(OemDetector.batteryOptimizationIntent(this))
            } catch (e: Exception) {
                Log.w(TAG, "Could not open battery optimization dialog", e)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATION,
                )
            }
        }
    }

    companion object {
        private const val TAG = "NovaTerm"
        private const val REQ_NOTIFICATION = 100
    }
}
