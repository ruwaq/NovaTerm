package com.novaterm.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.novaterm.app.service.TerminalService
import com.novaterm.app.ui.theme.NovaTermTheme

class MainActivity : ComponentActivity() {

    var terminalService: TerminalService? by mutableStateOf(null)
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            terminalService = (binder as TerminalService.LocalBinder).service
            if (terminalService?.sessions?.isEmpty() == true) {
                terminalService?.createSession()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            terminalService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        startTerminalService()

        setContent {
            NovaTermTheme {
                NovaTermApp(
                    service = terminalService,
                    onNewSession = { terminalService?.createSession() },
                    onCloseSession = { index -> terminalService?.removeSession(index) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, TerminalService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
            // Service was not bound
        }
    }

    private fun startTerminalService() {
        val intent = Intent(this, TerminalService::class.java)
        startForegroundService(intent)
    }
}
