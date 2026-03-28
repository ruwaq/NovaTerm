package com.novaterm.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.novaterm.app.service.TerminalService
import com.novaterm.app.ui.theme.NovaTermTheme
import com.novaterm.app.ui.viewmodel.TerminalViewModel

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

        // Request POST_NOTIFICATIONS permission (required Android 13+)
        requestNotificationPermission()

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
        private const val REQ_NOTIFICATION = 100
    }
}
