package com.novaterm.app.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novaterm.app.service.TerminalService
import com.novaterm.feature.settings.data.PreferencesRepository
import com.novaterm.feature.settings.data.TerminalPreferences
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel that owns the service binding and all terminal UI state.
 *
 * Survives configuration changes and process death (for the binding -
 * the service itself is a foreground service that persists independently).
 * Exposes sessions as [StateFlow] so Compose observes changes reactively.
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val preferences: StateFlow<TerminalPreferences> = prefsRepo.preferences

    // ── Service binding ────────────────────────────────────

    private val _service = MutableStateFlow<TerminalService?>(null)
    val service: StateFlow<TerminalService?> = _service.asStateFlow()

    /**
     * Sessions derived from the bound service's StateFlow.
     * When service is null, emits empty list.
     */
    val sessions: StateFlow<List<TerminalSession>> = _service
        .flatMapLatest { svc -> svc?.sessions ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as? TerminalService.LocalBinder)?.service ?: return
            _service.value = svc
            if (svc.sessionCount == 0) {
                val saved = svc.getSavedSessions()
                if (saved.isNotEmpty()) {
                    // Restore sessions in their original directories
                    saved.forEach { meta ->
                        svc.createSessionInDir(meta.cwd)
                    }
                    svc.clearSavedSessions()
                } else {
                    svc.createSession()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            _service.value = null
        }
    }

    private var bound = false

    fun bindService() {
        if (bound) return
        val app = getApplication<Application>()
        app.bindService(
            Intent(app, TerminalService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE or Context.BIND_ABOVE_CLIENT,
        )
        bound = true
    }

    fun unbindService() {
        if (!bound) return
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
            // Already unbound
        }
        bound = false
    }

    override fun onCleared() {
        unbindService()
        super.onCleared()
    }

    // ── Session UI state ───────────────────────────────────

    private val _currentSessionIndex = MutableStateFlow(0)
    val currentSessionIndex: StateFlow<Int> = _currentSessionIndex.asStateFlow()

    /** Custom tab names set by the user (index → name). Overrides terminal title. */
    private val _sessionNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val sessionNames: StateFlow<Map<Int, String>> = _sessionNames.asStateFlow()

    private val _ctrlActive = MutableStateFlow(false)
    val ctrlActive: StateFlow<Boolean> = _ctrlActive.asStateFlow()

    private val _altActive = MutableStateFlow(false)
    val altActive: StateFlow<Boolean> = _altActive.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _showOnboarding = MutableStateFlow(!prefsRepo.isOnboardingCompleted)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    // ── Actions ────────────────────────────────────────────

    fun createSession() {
        val svc = _service.value ?: return
        svc.createSession() ?: return
        _currentSessionIndex.value = svc.sessionCount - 1
    }

    // ── Session close confirmation ─────────────────────────

    private val _sessionToClose = MutableStateFlow<Int?>(null)
    val sessionToClose: StateFlow<Int?> = _sessionToClose.asStateFlow()

    fun requestCloseSession(index: Int) {
        val svc = _service.value ?: return
        val sessions = svc.sessions.value
        // If session is still running, ask confirmation
        if (index in sessions.indices && sessions[index].isRunning) {
            _sessionToClose.value = index
        } else {
            removeSession(index)
        }
    }

    fun confirmCloseSession() {
        _sessionToClose.value?.let { removeSession(it) }
        _sessionToClose.value = null
    }

    fun cancelCloseSession() {
        _sessionToClose.value = null
    }

    private fun removeSession(index: Int) {
        val svc = _service.value ?: return
        svc.removeSession(index)
        val newCount = svc.sessionCount
        if (_currentSessionIndex.value >= newCount) {
            _currentSessionIndex.value = (newCount - 1).coerceAtLeast(0)
        }
    }

    fun selectSession(index: Int) {
        _currentSessionIndex.value = index
    }

    fun renameSession(index: Int, name: String) {
        _sessionNames.update { it + (index to name) }
    }

    fun getSessionName(index: Int): String? = _sessionNames.value[index]

    fun toggleCtrl() {
        _ctrlActive.value = !_ctrlActive.value
    }

    fun toggleAlt() {
        _altActive.value = !_altActive.value
    }

    fun resetModifiers() {
        _ctrlActive.value = false
        _altActive.value = false
    }

    fun updatePreferences(newPrefs: TerminalPreferences) {
        prefsRepo.update(newPrefs)
    }

    fun completeOnboarding(colorScheme: String) {
        prefsRepo.update(prefsRepo.preferences.value.copy(colorScheme = colorScheme))
        prefsRepo.completeOnboarding()
        _showOnboarding.value = false
    }

    fun showSettings() {
        _showSettings.value = true
    }

    fun hideSettings() {
        _showSettings.value = false
    }

    fun toggleWakeLock() {
        _service.value?.toggleWakeLock()
    }
}
