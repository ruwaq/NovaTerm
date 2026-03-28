package com.novaterm.core.config

import app.cash.turbine.test
import com.novaterm.core.common.contract.ConfigStore
import com.novaterm.core.common.model.ColorScheme
import com.novaterm.core.common.model.TerminalConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * In-memory ConfigStore for testing without Android DataStore.
 */
private class InMemoryConfigStore(
    initial: TerminalConfig = TerminalConfig(),
) : ConfigStore {
    private val _config = MutableStateFlow(initial)
    override val config: StateFlow<TerminalConfig> = _config.asStateFlow()

    override suspend fun update(transform: (TerminalConfig) -> TerminalConfig) {
        _config.value = transform(_config.value)
    }
}

class InMemoryConfigStoreTest {

    @Test
    fun `initial config has correct defaults`() {
        val store = InMemoryConfigStore()
        val config = store.config.value

        assertEquals(32, config.fontSize)
        assertEquals("monospace", config.fontFamily)
        assertEquals(ColorScheme.GRUVBOX_DARK, config.colorScheme)
        assertEquals(10_000, config.scrollbackLines)
        assertEquals(true, config.backIsEscape)
        assertEquals(true, config.bellEnabled)
        assertEquals(true, config.hapticFeedback)
        assertEquals(true, config.showExtraKeys)
        assertEquals("xterm-256color", config.terminalType)
    }

    @Test
    fun `update transforms config`() = runTest {
        val store = InMemoryConfigStore()

        store.update { it.copy(fontSize = 24) }
        assertEquals(24, store.config.value.fontSize)
    }

    @Test
    fun `config flow emits on updates`() = runTest {
        val store = InMemoryConfigStore()

        store.config.test {
            assertEquals(32, awaitItem().fontSize)

            store.update { it.copy(fontSize = 20) }
            assertEquals(20, awaitItem().fontSize)

            store.update { it.copy(colorScheme = ColorScheme.DRACULA) }
            assertEquals(ColorScheme.DRACULA, awaitItem().colorScheme)
        }
    }

    @Test
    fun `multiple updates preserve non-changed fields`() = runTest {
        val store = InMemoryConfigStore()

        store.update { it.copy(fontSize = 24) }
        store.update { it.copy(scrollbackLines = 50_000) }

        val result = store.config.value
        assertEquals(24, result.fontSize)
        assertEquals(50_000, result.scrollbackLines)
        assertEquals(ColorScheme.GRUVBOX_DARK, result.colorScheme) // unchanged
    }

    @Test
    fun `update is atomic - transform receives current state`() = runTest {
        val store = InMemoryConfigStore()

        store.update { it.copy(fontSize = 20) }
        store.update { current ->
            assertEquals(20, current.fontSize)
            current.copy(fontSize = current.fontSize + 2)
        }

        assertEquals(22, store.config.value.fontSize)
    }
}
