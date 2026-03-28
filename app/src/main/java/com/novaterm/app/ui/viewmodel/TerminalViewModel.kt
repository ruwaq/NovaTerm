package com.novaterm.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.novaterm.feature.settings.data.PreferencesRepository
import com.novaterm.feature.settings.data.TerminalPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)

    val preferences: StateFlow<TerminalPreferences> = prefsRepo.preferences

    private val _currentSessionIndex = MutableStateFlow(0)
    val currentSessionIndex: StateFlow<Int> = _currentSessionIndex.asStateFlow()

    private val _ctrlActive = MutableStateFlow(false)
    val ctrlActive: StateFlow<Boolean> = _ctrlActive.asStateFlow()

    private val _altActive = MutableStateFlow(false)
    val altActive: StateFlow<Boolean> = _altActive.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    fun selectSession(index: Int) {
        _currentSessionIndex.value = index
    }

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

    fun showSettings() {
        _showSettings.value = true
    }

    fun hideSettings() {
        _showSettings.value = false
    }
}
