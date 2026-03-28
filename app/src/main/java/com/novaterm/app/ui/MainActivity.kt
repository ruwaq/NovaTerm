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
import androidx.compose.runtime.collectAsState
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
        requestStoragePermission()
        requestBatteryExemptionIfNeeded()

        // Start the foreground service (idempotent if already running)
        startForegroundService(
            Intent(this, TerminalService::class.java)
        )

        setContent {
            val preferences = viewModel.preferences.collectAsState()
            val isDarkScheme = !preferences.value.colorScheme.contains("light")

            NovaTermTheme(darkTheme = isDarkScheme) {
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

    private fun requestStoragePermission() {
        // For Android 10-12, request legacy storage permission for ~/storage/ symlinks.
        // Android 13+ uses granular media permissions which we don't need (we access via SAF).
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQ_STORAGE,
                )
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
        private const val REQ_STORAGE = 101
    }
}
