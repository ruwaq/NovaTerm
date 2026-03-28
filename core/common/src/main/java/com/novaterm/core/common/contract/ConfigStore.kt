package com.novaterm.core.common.contract

import com.novaterm.core.common.model.TerminalConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for persisted terminal configuration.
 * Implementations handle Android SharedPreferences, DataStore, etc.
 */
interface ConfigStore {

    /** Observable config state. Always has a value (defaults on first read). */
    val config: StateFlow<TerminalConfig>

    /**
     * Atomically update the config. Suspend because persistence may involve I/O.
     * The [transform] receives the current value and returns the new one.
     * Implementations must guarantee that the StateFlow is updated before returning.
     */
    suspend fun update(transform: (TerminalConfig) -> TerminalConfig)
}
