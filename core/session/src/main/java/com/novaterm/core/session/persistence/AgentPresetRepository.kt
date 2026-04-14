package com.novaterm.core.session.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novaterm.core.session.manager.AgentPreset
import com.novaterm.core.session.manager.AgentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Top-level DataStore delegate for agent presets.
 * Must be at top level to avoid creating multiple DataStore instances.
 */
private val Context.agentPresetsDataStore by preferencesDataStore(name = "novaterm_agent_presets")

/**
 * Persists agent presets to DataStore.
 *
 * Built-in presets (Claude Code, Gemini CLI, etc.) are always available
 * and cannot be deleted. Custom presets are stored as JSON strings
 * in DataStore preferences.
 */
class AgentPresetRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val dataStore = context.agentPresetsDataStore

    private val _presets = MutableStateFlow(AgentPreset.BUILT_INS)
    val presets: StateFlow<List<AgentPreset>> = _presets.asStateFlow()

    init {
        scope.launch {
            loadPresets()
        }
    }

    /**
     * Add a custom preset. Built-in presets cannot be overridden.
     */
    suspend fun addPreset(preset: AgentPreset) {
        if (AgentPreset.BUILT_INS.any { it.id == preset.id }) {
            return // Cannot override built-in presets
        }
        dataStore.edit { prefs ->
            val existing = loadCustomPresetsFrom(prefs).toMutableList()
            existing.removeAll { it.id == preset.id }
            existing.add(preset)
            prefs[CUSTOM_PRESETS_KEY] = existing.map { it.toJson().toString() }.toSet()
        }
        loadPresets()
    }

    /**
     * Remove a custom preset. Built-in presets cannot be removed.
     */
    suspend fun removePreset(presetId: String) {
        if (AgentPreset.BUILT_INS.any { it.id == presetId }) {
            return // Cannot remove built-in presets
        }
        dataStore.edit { prefs ->
            val existing = loadCustomPresetsFrom(prefs).toMutableList()
            existing.removeAll { it.id == presetId }
            prefs[CUSTOM_PRESETS_KEY] = existing.map { it.toJson().toString() }.toSet()
        }
        loadPresets()
    }

    /**
     * Update a custom preset. Built-in presets cannot be updated.
     */
    suspend fun updatePreset(preset: AgentPreset) {
        if (AgentPreset.BUILT_INS.any { it.id == preset.id }) {
            return
        }
        dataStore.edit { prefs ->
            val existing = loadCustomPresetsFrom(prefs).toMutableList()
            val index = existing.indexOfFirst { it.id == preset.id }
            if (index >= 0) {
                existing[index] = preset
            }
            prefs[CUSTOM_PRESETS_KEY] = existing.map { it.toJson().toString() }.toSet()
        }
        loadPresets()
    }

    private suspend fun loadPresets() {
        dataStore.data.collect { prefs ->
            val customPresets = loadCustomPresetsFrom(prefs)
            _presets.value = AgentPreset.BUILT_INS + customPresets
        }
    }

    private fun loadCustomPresetsFrom(prefs: androidx.datastore.preferences.core.Preferences): List<AgentPreset> {
        val jsonStrings = prefs[CUSTOM_PRESETS_KEY] ?: emptySet()
        return jsonStrings.mapNotNull { json ->
            try {
                AgentPreset.fromJson(JSONObject(json))
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private val CUSTOM_PRESETS_KEY = stringSetPreferencesKey("custom_agent_presets")
    }
}

// Extension functions for AgentPreset serialization
private fun AgentPreset.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("agentType", agentType.name)
    put("command", command)
    put("args", org.json.JSONArray(args))
    put("workingDir", workingDir)
    put("isBuiltIn", isBuiltIn)
    put("icon", icon)
    val envObj = JSONObject()
    envOverrides.forEach { (k, v) -> envObj.put(k, v) }
    put("envOverrides", envObj)
}

private fun AgentPreset.Companion.fromJson(json: JSONObject): AgentPreset = AgentPreset(
    id = json.getString("id"),
    name = json.getString("name"),
    agentType = AgentType.fromName(json.optString("agentType", "CUSTOM")),
    command = json.getString("command"),
    args = json.optJSONArray("args")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList(),
    workingDir = json.optString("workingDir", ""),
    isBuiltIn = json.optBoolean("isBuiltIn", false),
    icon = json.optString("icon", ""),
    envOverrides = json.optJSONObject("envOverrides")?.let { obj ->
        val map = mutableMapOf<String, String>()
        for (key in obj.keys()) {
            map[key] = obj.getString(key)
        }
        map
    } ?: emptyMap(),
)