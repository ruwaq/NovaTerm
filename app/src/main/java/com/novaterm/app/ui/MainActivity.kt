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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.novaterm.app.service.TerminalService
import com.novaterm.app.ui.theme.NovaTermTheme
import com.novaterm.app.ui.viewmodel.TerminalViewModel
import com.novaterm.feature.oemcompat.detection.OemDetector

/**
 * Single activity hosting the terminal UI.
 *
 * Lifecycle:
 *   1. OEM battery guide (aggressive OEMs only, first launch)
 *   2. Bootstrap (first launch — extracts packages)
 *   3. Start foreground service (AFTER bootstrap completes)
 *   4. Main terminal UI
 *
 * Service binding is owned by [TerminalViewModel] so it survives
 * configuration changes. The activity only starts the foreground
 * service and delegates binding lifecycle to the ViewModel.
 *
 * Permissions use the modern ActivityResult API with graceful degradation:
 * - Notification permission: optional, foreground service works without it
 * - Storage permission: optional, DocumentsProvider still works
 */
class MainActivity : ComponentActivity() {

    private val viewModel: TerminalViewModel by viewModels()

    // Modern permission launchers — replace legacy requestPermissions()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "Notification permission granted")
        } else {
            Log.w(TAG, "Notification permission denied — service notification may be hidden")
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "Storage permission granted")
        } else {
            Log.w(TAG, "Storage permission denied — DocumentsProvider still available")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestPermissions()

        // Only start service if already bootstrapped (returning user).
        // First-time users: service starts in BootstrapScreen.onComplete.
        val installer = com.novaterm.core.bootstrap.BootstrapInstaller(applicationContext)
        if (installer.isBootstrapped) {
            startTerminalService()
        }

        setContent {
            val preferences = viewModel.preferences.collectAsState()
            val isDarkScheme = com.novaterm.core.common.model.ColorSchemes.isDark(preferences.value.colorScheme)
            val installer = remember { com.novaterm.core.bootstrap.BootstrapInstaller(applicationContext) }
            val context = this@MainActivity

            var bootstrapped by remember { mutableStateOf(installer.isBootstrapped) }

            // OEM battery optimization state
            val oemInfo = remember { OemDetector.detect(context) }
            val prefs = remember { context.getSharedPreferences("novaterm_prefs", MODE_PRIVATE) }
            var batteryDismissed by remember {
                mutableStateOf(
                    prefs.getBoolean("battery_guide_dismissed", false) ||
                    !OemDetector.needsBatteryWhitelist(oemInfo)
                )
            }
            var needsWhitelist by remember { mutableStateOf(OemDetector.needsBatteryWhitelist(oemInfo)) }

            // Re-evaluate battery status when app resumes
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
                    // Step 1: OEM battery guide
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
                            onComplete = { success ->
                                bootstrapped = true
                                startTerminalService()
                                viewModel.bindService()
                                if (!success) {
                                    Log.w(TAG, "Bootstrap failed — shell falls back to /system/bin/sh")
                                }
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
        // Only bind if bootstrap is done — otherwise TerminalService creates
        // a session with /system/bin/sh instead of bash.
        val installer = com.novaterm.core.bootstrap.BootstrapInstaller(applicationContext)
        if (installer.isBootstrapped) {
            viewModel.bindService()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService()
    }

    /**
     * Start terminal foreground service with proper error handling.
     * On Android 12+, ForegroundServiceStartNotAllowedException can occur
     * if the app is in a restricted state.
     */
    private fun startTerminalService() {
        try {
            startForegroundService(Intent(this, TerminalService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start terminal service", e)
        }
    }

    /**
     * Request permissions using ActivityResult API.
     * Both are optional — the app degrades gracefully without them:
     * - Notifications: service runs but notification may be hidden
     * - Storage: DocumentsProvider and internal storage still work
     */
    private fun requestPermissions() {
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Storage permission (Android 12 and below only)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    companion object {
        private const val TAG = "NovaTerm"
    }
}
