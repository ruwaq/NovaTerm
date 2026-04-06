package com.novaterm.app.ui

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.util.Rational
import com.novaterm.app.BuildConfig
import android.widget.Toast
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
import java.util.Locale

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

    // Voice input via Android SpeechRecognizer — writes recognized text to active session
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = matches?.firstOrNull()
            if (!recognizedText.isNullOrBlank()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Voice input received (${recognizedText.length} chars)")
                writeVoiceInputToSession(recognizedText)
            }
        }
    }

    /**
     * Write recognized speech text to the active terminal session.
     */
    private fun writeVoiceInputToSession(text: String) {
        val session = viewModel.sessions.value.getOrNull(viewModel.currentSessionIndex.value) ?: return
        session.write(text)
    }

    /**
     * Launch the speech recognizer intent.
     * Uses ACTION_RECOGNIZE_SPEECH which handles its own permissions via the system dialog.
     */
    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Speech recognition not available", e)
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
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

        // Handle the launching intent (RUN_COMMAND, SEND, VIEW nvterm://)
        handleIncomingIntent(intent)

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
                        androidx.activity.compose.BackHandler {
                            prefs.edit().putBoolean("battery_guide_dismissed", true).apply()
                            batteryDismissed = true
                        }
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
                        NovaTermApp(
                            viewModel = viewModel,
                            onVoiceInput = ::launchVoiceInput,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Handle incoming intents from external apps:
     * - RUN_COMMAND: execute a command in a new or existing session
     * - SEND (text/plain): write shared text to the current session
     * - VIEW (nvterm://): deep link, e.g. nvterm://run?cmd=ls
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            ACTION_NEW_SESSION -> {
                Log.i(TAG, "NEW_SESSION shortcut intent")
                val serviceIntent = Intent(this, TerminalService::class.java).apply {
                    action = TerminalService.ACTION_NEW_SESSION
                }
                try {
                    startForegroundService(serviceIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot start service from background", e)
                }
            }

            ACTION_SWITCH_SESSION -> {
                val sessionIndex = intent.getIntExtra("session_index", -1)
                if (sessionIndex >= 0) {
                    Log.i(TAG, "SWITCH_SESSION shortcut intent: index=$sessionIndex")
                    viewModel.selectSession(sessionIndex)
                }
            }

            ACTION_LAUNCH_PRESET -> {
                val preset = intent.getStringExtra("preset")
                if (!preset.isNullOrBlank()) {
                    Log.i(TAG, "LAUNCH_PRESET shortcut intent: preset=$preset")
                    val serviceIntent = Intent(this, TerminalService::class.java).apply {
                        action = TerminalService.ACTION_LAUNCH_PRESET
                        putExtra("preset", preset)
                    }
                    try {
                        startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot start service from background", e)
                    }
                }
            }

            ACTION_RUN_COMMAND -> {
                // Verify caller has RUN_COMMAND permission
                if (checkCallingOrSelfPermission("com.nvterm.permission.RUN_COMMAND")
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "RUN_COMMAND denied — caller lacks permission")
                    return
                }
                val command = intent.getStringExtra("command")
                if (!command.isNullOrBlank()) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "RUN_COMMAND intent received")
                    val cwd = intent.getStringExtra("cwd")
                    val serviceIntent = Intent(this, TerminalService::class.java).apply {
                        action = TerminalService.ACTION_RUN_COMMAND
                        putExtra("command", command)
                        if (!cwd.isNullOrBlank()) putExtra("cwd", cwd)
                    }
                    try {
                        startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot start service from background", e)
                    }
                }
            }

            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "SEND intent received (${sharedText.length} chars)")
                        val serviceIntent = Intent(this, TerminalService::class.java).apply {
                            action = TerminalService.ACTION_WRITE_INPUT
                            putExtra("input", sharedText)
                        }
                        try {
                            startForegroundService(serviceIntent)
                        } catch (e: Exception) {
                            Log.w(TAG, "Cannot start service from background", e)
                        }
                    }
                }
            }

            TerminalService.ACTION_FLOAT -> {
                Log.i(TAG, "FLOAT action — entering PiP mode")
                enterPipModeForced()
            }

            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null && uri.scheme == "nvterm") {
                    if (BuildConfig.DEBUG) Log.d(TAG, "VIEW intent: nvterm://${uri.host}")
                    when (uri.host) {
                        "open" -> {
                            // nvterm://open — just bring NovaTerm to foreground
                            if (BuildConfig.DEBUG) Log.d(TAG, "Deep link: open")
                        }
                        "run" -> {
                            // SECURITY: nvterm://run?cmd= is disabled to prevent
                            // malicious websites from executing commands via deep links.
                            // Use the RUN_COMMAND intent with proper permission instead.
                            Log.w(TAG, "Deep link nvterm://run is disabled for security")
                            Toast.makeText(this, "Command execution via deep links is disabled", Toast.LENGTH_SHORT).show()
                        }
                        else -> Log.w(TAG, "Unknown nvterm:// host: ${uri.host}")
                    }
                }
            }
        }
    }

    /**
     * Enter Picture-in-Picture mode when the user navigates away.
     * This keeps the terminal visible as a floating window.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipModeIfEnabled()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setInPipMode(isInPictureInPictureMode)
    }

    private fun enterPipModeIfEnabled() {
        // Only enter PiP if user has enabled it in settings and has active sessions
        if (!viewModel.preferences.value.pipOnLeave) return
        if (viewModel.sessions.value.isEmpty()) return

        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(3, 4)) // Portrait terminal ratio
                .build()
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enter PiP mode", e)
        }
    }

    /**
     * Force-enter PiP mode regardless of user settings.
     * Used when the user explicitly taps the Float notification action.
     */
    private fun enterPipModeForced() {
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(3, 4)) // Portrait terminal ratio
                .build()
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enter PiP mode", e)
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

    // NOTE: We intentionally do NOT unbind in onStop().
    // Unbinding in onStop allows the OS (especially aggressive OEMs like Xiaomi)
    // to kill the foreground service when the user switches apps, erasing all
    // terminal content. The ViewModel manages unbinding in onCleared() instead,
    // which only fires when the Activity is truly finishing.

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
        private const val ACTION_RUN_COMMAND = "com.nvterm.RUN_COMMAND"
        private const val ACTION_NEW_SESSION = "com.nvterm.NEW_SESSION"
        private const val ACTION_SWITCH_SESSION = "com.nvterm.SWITCH_SESSION"
        private const val ACTION_LAUNCH_PRESET = "com.nvterm.LAUNCH_PRESET"
    }
}
