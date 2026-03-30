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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

        // Service starts AFTER bootstrap completes (in BootstrapScreen.onComplete)
        // or immediately if already bootstrapped
        val installer = com.novaterm.core.bootstrap.BootstrapInstaller(applicationContext)
        if (installer.isBootstrapped) {
            startForegroundService(Intent(this, TerminalService::class.java))
        }

        setContent {
            val preferences = viewModel.preferences.collectAsState()
            val isDarkScheme = com.novaterm.core.common.model.ColorSchemes.isDark(preferences.value.colorScheme)
            val installer = remember { com.novaterm.core.bootstrap.BootstrapInstaller(applicationContext) }
            val context = this@MainActivity

            var bootstrapped by remember { mutableStateOf(installer.isBootstrapped) }

            // OEM battery optimization state — managed inside Compose
            val oemInfo = remember { OemDetector.detect(context) }
            val prefs = remember { context.getSharedPreferences("novaterm_prefs", MODE_PRIVATE) }
            var batteryDismissed by remember {
                mutableStateOf(
                    prefs.getBoolean("battery_guide_dismissed", false) ||
                    !OemDetector.needsBatteryWhitelist(oemInfo)
                )
            }
            // Re-check battery status when returning from Settings
            var needsWhitelist by remember { mutableStateOf(OemDetector.needsBatteryWhitelist(oemInfo)) }

            // Re-evaluate battery status when app resumes (user may have changed Settings)
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val fresh = OemDetector.detect(context)
                        needsWhitelist = OemDetector.needsBatteryWhitelist(fresh)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            NovaTermTheme(darkTheme = isDarkScheme) {
                when {
                    // Step 1: OEM battery guide (aggressive OEMs only, first launch)
                    !batteryDismissed -> {
                        com.novaterm.feature.oemcompat.ui.OemGuideScreen(
                            oemInfo = oemInfo,
                            needsWhitelist = needsWhitelist,
                            instructions = OemDetector.getInstructions(oemInfo.brand),
                            onRequestBatteryExemption = {
                                try {
                                    context.startActivity(
                                        OemDetector.batteryOptimizationIntent(context)
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not open battery settings", e)
                                }
                            },
                            onOpenAutostartSettings = OemDetector.autostartSettingsIntent(
                                context, oemInfo.brand
                            )?.let { intent ->
                                {
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Could not open autostart settings", e)
                                    }
                                }
                            },
                            onBack = {
                                prefs.edit().putBoolean("battery_guide_dismissed", true).apply()
                                batteryDismissed = true
                            },
                        )
                    }

                    // Step 2: Bootstrap (first launch only)
                    !bootstrapped -> {
                        BootstrapScreen(
                            installer = installer,
                            onComplete = {
                                bootstrapped = true
                                startForegroundService(
                                    Intent(context, TerminalService::class.java)
                                )
                            },
                        )
                    }

                    // Step 3: Main terminal
                    else -> {
                        NovaTermApp(viewModel = viewModel)
                    }
                }
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

    private fun requestStoragePermission() {
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
