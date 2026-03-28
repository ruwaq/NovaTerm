package com.novaterm.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
}
