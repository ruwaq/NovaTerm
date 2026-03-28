package com.novaterm.core.common.contract

import com.novaterm.core.common.model.TerminalConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for persisted terminal configuration.
 * Implementations handle Android SharedPreferences, DataStore, etc.
 */
interface ConfigStore {

    val config: StateFlow<TerminalConfig>

    fun update(transform: (TerminalConfig) -> TerminalConfig)
}
