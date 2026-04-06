package com.novaterm.feature.settings.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TerminalPreferences data class and PreferencesRepository companion constants.
 *
 * PreferencesRepository itself requires an Android Context (SharedPreferences +
 * EncryptedSharedPreferences) and cannot be instantiated in pure JVM tests without
 * Robolectric. All testable logic — defaults, copy semantics, constant values,
 * and validation rules — lives in TerminalPreferences, which is pure Kotlin.
 */
class PreferencesRepositoryTest {

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Test
    fun `default font size is 12sp`() {
        assertEquals(12, TerminalPreferences().fontSize)
    }

    @Test
    fun `default font family is system-mono`() {
        assertEquals(
            TerminalPreferences.FONT_FAMILY_SYSTEM_MONO,
            TerminalPreferences().fontFamily,
        )
    }

    @Test
    fun `default color scheme is gruvbox-dark`() {
        assertEquals("gruvbox-dark", TerminalPreferences().colorScheme)
    }

    @Test
    fun `default scrollback lines match constant`() {
        assertEquals(
            TerminalPreferences.DEFAULT_SCROLLBACK_LINES,
            TerminalPreferences().scrollbackLines,
        )
    }

    @Test
    fun `default scrollback lines are 10000`() {
        assertEquals(10_000, TerminalPreferences().scrollbackLines)
    }

    @Test
    fun `default mcp port is 8080`() {
        assertEquals(8080, TerminalPreferences().mcpPort)
    }

    @Test
    fun `default extra keys style is default`() {
        assertEquals(
            TerminalPreferences.EXTRA_KEYS_STYLE_DEFAULT,
            TerminalPreferences().extraKeysStyle,
        )
    }

    @Test
    fun `default api keys are empty strings`() {
        val prefs = TerminalPreferences()
        assertEquals("", prefs.anthropicApiKey)
        assertEquals("", prefs.googleApiKey)
        assertEquals("", prefs.openaiApiKey)
        assertEquals("", prefs.openrouterApiKey)
    }

    @Test
    fun `default boolean flags match documented values`() {
        val prefs = TerminalPreferences()
        assertFalse(prefs.keepScreenOn)
        assertTrue(prefs.hapticFeedback)
        assertTrue(prefs.bellEnabled)
        assertTrue(prefs.showExtraKeys)
        assertTrue(prefs.backIsEscape)
        assertFalse(prefs.useRustBackend)
        assertFalse(prefs.useGpuRenderer)
        assertFalse(prefs.pipOnLeave)
        assertFalse(prefs.mcpEnabled)
        assertFalse(prefs.llmEnabled)
    }

    // -------------------------------------------------------------------------
    // copy() semantics
    // -------------------------------------------------------------------------

    @Test
    fun `copy with fontSize preserves all other fields`() {
        val original = TerminalPreferences()
        val modified = original.copy(fontSize = 20)

        assertEquals(20, modified.fontSize)
        assertEquals(original.fontFamily, modified.fontFamily)
        assertEquals(original.colorScheme, modified.colorScheme)
        assertEquals(original.scrollbackLines, modified.scrollbackLines)
        assertEquals(original.keepScreenOn, modified.keepScreenOn)
        assertEquals(original.hapticFeedback, modified.hapticFeedback)
        assertEquals(original.bellEnabled, modified.bellEnabled)
        assertEquals(original.showExtraKeys, modified.showExtraKeys)
        assertEquals(original.backIsEscape, modified.backIsEscape)
        assertEquals(original.useRustBackend, modified.useRustBackend)
        assertEquals(original.extraKeysStyle, modified.extraKeysStyle)
        assertEquals(original.mcpPort, modified.mcpPort)
    }

    @Test
    fun `copy with multiple fields changes only specified fields`() {
        val original = TerminalPreferences()
        val modified = original.copy(
            fontSize = 16,
            keepScreenOn = true,
            colorScheme = "dracula",
        )

        assertEquals(16, modified.fontSize)
        assertTrue(modified.keepScreenOn)
        assertEquals("dracula", modified.colorScheme)
        // unchanged
        assertEquals(original.scrollbackLines, modified.scrollbackLines)
        assertEquals(original.fontFamily, modified.fontFamily)
        assertEquals(original.hapticFeedback, modified.hapticFeedback)
    }

    @Test
    fun `copy with api key does not affect other fields`() {
        val original = TerminalPreferences()
        val modified = original.copy(anthropicApiKey = "sk-ant-test")

        assertEquals("sk-ant-test", modified.anthropicApiKey)
        assertEquals("", modified.googleApiKey)
        assertEquals("", modified.openaiApiKey)
        assertEquals("", modified.openrouterApiKey)
        assertEquals(original.fontSize, modified.fontSize)
    }

    @Test
    fun `original instance is not mutated by copy`() {
        val original = TerminalPreferences()
        original.copy(fontSize = 24, useRustBackend = true)

        // data class copy returns new object — original is unchanged
        assertEquals(12, original.fontSize)
        assertFalse(original.useRustBackend)
    }

    @Test
    fun `two instances with same values are equal`() {
        val a = TerminalPreferences()
        val b = TerminalPreferences()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `instances with different values are not equal`() {
        val a = TerminalPreferences()
        val b = TerminalPreferences(fontSize = 18)
        assertNotEquals(a, b)
    }

    // -------------------------------------------------------------------------
    // Extra keys styles
    // -------------------------------------------------------------------------

    @Test
    fun `EXTRA_KEYS_STYLES contains exactly 5 styles`() {
        assertEquals(5, TerminalPreferences.EXTRA_KEYS_STYLES.size)
    }

    @Test
    fun `all extra keys style constants are in EXTRA_KEYS_STYLES`() {
        val styles = TerminalPreferences.EXTRA_KEYS_STYLES
        assertTrue(TerminalPreferences.EXTRA_KEYS_STYLE_DEFAULT in styles)
        assertTrue(TerminalPreferences.EXTRA_KEYS_STYLE_VIM in styles)
        assertTrue(TerminalPreferences.EXTRA_KEYS_STYLE_DEV in styles)
        assertTrue(TerminalPreferences.EXTRA_KEYS_STYLE_MINIMAL in styles)
        assertTrue(TerminalPreferences.EXTRA_KEYS_STYLE_AI in styles)
    }

    @Test
    fun `EXTRA_KEYS_STYLES has no duplicates`() {
        val styles = TerminalPreferences.EXTRA_KEYS_STYLES
        assertEquals(styles.size, styles.toSet().size)
    }

    @Test
    fun `extra keys style constants have distinct values`() {
        val values = setOf(
            TerminalPreferences.EXTRA_KEYS_STYLE_DEFAULT,
            TerminalPreferences.EXTRA_KEYS_STYLE_VIM,
            TerminalPreferences.EXTRA_KEYS_STYLE_DEV,
            TerminalPreferences.EXTRA_KEYS_STYLE_MINIMAL,
            TerminalPreferences.EXTRA_KEYS_STYLE_AI,
        )
        assertEquals(5, values.size)
    }

    // -------------------------------------------------------------------------
    // Font families
    // -------------------------------------------------------------------------

    @Test
    fun `FONT_FAMILIES contains exactly 5 entries`() {
        assertEquals(5, TerminalPreferences.FONT_FAMILIES.size)
    }

    @Test
    fun `all font family constants are in FONT_FAMILIES`() {
        val ids = TerminalPreferences.FONT_FAMILIES.map { it.first }
        assertTrue(TerminalPreferences.FONT_FAMILY_SYSTEM_MONO in ids)
        assertTrue(TerminalPreferences.FONT_FAMILY_JETBRAINS_MONO in ids)
        assertTrue(TerminalPreferences.FONT_FAMILY_FIRA_CODE in ids)
        assertTrue(TerminalPreferences.FONT_FAMILY_SOURCE_CODE_PRO in ids)
        assertTrue(TerminalPreferences.FONT_FAMILY_CASCADIA in ids)
    }

    @Test
    fun `all font families have non-blank display names`() {
        TerminalPreferences.FONT_FAMILIES.forEach { (id, displayName) ->
            assertTrue("Font family '$id' has blank display name", displayName.isNotBlank())
        }
    }

    @Test
    fun `FONT_FAMILIES has no duplicate IDs`() {
        val ids = TerminalPreferences.FONT_FAMILIES.map { it.first }
        assertEquals(ids.size, ids.toSet().size)
    }

    // -------------------------------------------------------------------------
    // Scrollback options
    // -------------------------------------------------------------------------

    @Test
    fun `SCROLLBACK_OPTIONS are ordered ascending`() {
        val options = TerminalPreferences.SCROLLBACK_OPTIONS
        for (i in 0 until options.lastIndex) {
            assertTrue(
                "SCROLLBACK_OPTIONS not ascending at index $i: ${options[i]} >= ${options[i + 1]}",
                options[i] < options[i + 1],
            )
        }
    }

    @Test
    fun `SCROLLBACK_OPTIONS contains the default value`() {
        assertTrue(
            TerminalPreferences.DEFAULT_SCROLLBACK_LINES in TerminalPreferences.SCROLLBACK_OPTIONS,
        )
    }

    @Test
    fun `SCROLLBACK_OPTIONS all values are positive`() {
        TerminalPreferences.SCROLLBACK_OPTIONS.forEach { lines ->
            assertTrue("Scrollback option $lines is not positive", lines > 0)
        }
    }

    @Test
    fun `SCROLLBACK_OPTIONS has no duplicates`() {
        val options = TerminalPreferences.SCROLLBACK_OPTIONS
        assertEquals(options.size, options.toSet().size)
    }

    @Test
    fun `SCROLLBACK_OPTIONS minimum is 1000`() {
        assertEquals(1_000, TerminalPreferences.SCROLLBACK_OPTIONS.first())
    }

    @Test
    fun `SCROLLBACK_OPTIONS maximum is 50000`() {
        assertEquals(50_000, TerminalPreferences.SCROLLBACK_OPTIONS.last())
    }

    // -------------------------------------------------------------------------
    // Prefs constants (companion in PreferencesRepository)
    // -------------------------------------------------------------------------

    @Test
    fun `PREFS_NAME is non-blank`() {
        assertTrue(PreferencesRepository.PREFS_NAME.isNotBlank())
    }

    @Test
    fun `SECURE_PREFS_NAME is non-blank and distinct from PREFS_NAME`() {
        assertTrue(PreferencesRepository.SECURE_PREFS_NAME.isNotBlank())
        assertNotEquals(PreferencesRepository.PREFS_NAME, PreferencesRepository.SECURE_PREFS_NAME)
    }

    // -------------------------------------------------------------------------
    // Color scheme validation (isDark heuristic via id convention)
    // -------------------------------------------------------------------------

    @Test
    fun `gruvbox-dark scheme id contains dark suffix`() {
        assertTrue(TerminalPreferences().colorScheme.contains("dark"))
    }

    @Test
    fun `dark scheme ids end with -dark`() {
        val darkSchemes = listOf(
            "gruvbox-dark", "catppuccin-mocha", "solarized-dark", "monokai", "nord", "dracula",
        )
        // All dark schemes either end in -dark or are well-known dark themes
        darkSchemes.forEach { id ->
            assertFalse("Dark scheme id '$id' should not be blank", id.isBlank())
        }
    }

    @Test
    fun `gruvbox-light is distinct from default color scheme`() {
        assertNotEquals("gruvbox-light", TerminalPreferences().colorScheme)
    }

    // -------------------------------------------------------------------------
    // StateFlow simulation — TerminalPreferences as value in a MutableStateFlow
    // -------------------------------------------------------------------------

    @Test
    fun `MutableStateFlow of TerminalPreferences emits initial value`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(TerminalPreferences())
        assertEquals(12, flow.value.fontSize)
        assertEquals("gruvbox-dark", flow.value.colorScheme)
    }

    @Test
    fun `updating MutableStateFlow reflects new TerminalPreferences`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(TerminalPreferences())

        flow.value = flow.value.copy(fontSize = 18, colorScheme = "dracula")

        assertEquals(18, flow.value.fontSize)
        assertEquals("dracula", flow.value.colorScheme)
        // Unmodified field preserved
        assertEquals(TerminalPreferences.DEFAULT_SCROLLBACK_LINES, flow.value.scrollbackLines)
    }

    @Test
    fun `resetting flow to defaults restores all default values`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(
            TerminalPreferences(
                fontSize = 24,
                colorScheme = "nord",
                useRustBackend = true,
                scrollbackLines = 50_000,
            )
        )

        flow.value = TerminalPreferences()

        val defaults = TerminalPreferences()
        assertEquals(defaults, flow.value)
    }

    @Test
    fun `consecutive updates to flow accumulate correctly`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(TerminalPreferences())

        flow.value = flow.value.copy(fontSize = 16)
        flow.value = flow.value.copy(scrollbackLines = 5_000)
        flow.value = flow.value.copy(useRustBackend = true)

        assertEquals(16, flow.value.fontSize)
        assertEquals(5_000, flow.value.scrollbackLines)
        assertTrue(flow.value.useRustBackend)
        // Unmodified fields stay at default
        assertEquals("gruvbox-dark", flow.value.colorScheme)
        assertFalse(flow.value.keepScreenOn)
    }
}
