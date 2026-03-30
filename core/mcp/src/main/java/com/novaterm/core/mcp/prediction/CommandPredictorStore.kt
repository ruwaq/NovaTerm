package com.novaterm.core.mcp.prediction

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JSON persistence for CommandPredictor model.
 *
 * Stores bigrams, unigrams, and CWD context as a single JSON file.
 * Typical size: <1MB for 100K commands. Load/save is <50ms.
 */
object CommandPredictorStore {

    private const val FILE_NAME = "command_predictor.json"

    private const val KEY_VERSION = "version"
    private const val KEY_TOTAL = "totalCommands"
    private const val KEY_UNIGRAMS = "unigrams"
    private const val KEY_BIGRAMS = "bigrams"
    private const val KEY_CWD = "cwdCommands"
    private const val CURRENT_VERSION = 1

    /**
     * Save predictor state to JSON file.
     *
     * @param predictor The predictor to serialize.
     * @param directory App-private directory to store the file.
     */
    /**
     * @return true if save succeeded, false on I/O error.
     */
    fun save(predictor: CommandPredictor, directory: File): Boolean {
        return try {
            val json = JSONObject().apply {
                put(KEY_VERSION, CURRENT_VERSION)
                put(KEY_TOTAL, predictor.totalLearned)
                put(KEY_UNIGRAMS, mapToJson(predictor.exportUnigrams()))
                put(KEY_BIGRAMS, nestedMapToJson(predictor.exportBigrams()))
                put(KEY_CWD, nestedMapToJson(predictor.exportCwdCommands()))
            }
            // Atomic write: write to temp file then rename to prevent corruption
            val file = File(directory, FILE_NAME)
            val tmp = File(directory, "$FILE_NAME.tmp")
            tmp.writeText(json.toString())
            tmp.renameTo(file)
            true
        } catch (e: Exception) {
            Log.w("CommandPredictorStore", "Failed to save predictor", e)
            false
        }
    }

    /**
     * Load predictor state from JSON file.
     *
     * @param directory App-private directory where the file is stored.
     * @return Restored CommandPredictor, or a fresh one if no file exists.
     */
    fun load(directory: File): CommandPredictor {
        val file = File(directory, FILE_NAME)
        if (!file.exists()) return CommandPredictor()

        return try {
            val json = JSONObject(file.readText())
            val version = json.optInt(KEY_VERSION, 0)
            if (version != CURRENT_VERSION) return CommandPredictor()

            val predictor = CommandPredictor()
            predictor.importState(
                unigrams = jsonToMap(json.getJSONObject(KEY_UNIGRAMS)),
                bigrams = jsonToNestedMap(json.getJSONObject(KEY_BIGRAMS)),
                cwdCommands = jsonToNestedMap(json.getJSONObject(KEY_CWD)),
                totalCommands = json.getInt(KEY_TOTAL),
            )
            predictor
        } catch (_: Exception) {
            CommandPredictor()
        }
    }

    // ── JSON helpers ────────────────────────────────────────

    private fun mapToJson(map: Map<String, Int>): JSONObject {
        return JSONObject().apply {
            for ((key, value) in map) put(key, value)
        }
    }

    private fun nestedMapToJson(map: Map<String, Map<String, Int>>): JSONObject {
        return JSONObject().apply {
            for ((key, inner) in map) put(key, mapToJson(inner))
        }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Int> {
        val map = HashMap<String, Int>(json.length())
        for (key in json.keys()) {
            map[key] = json.getInt(key)
        }
        return map
    }

    private fun jsonToNestedMap(json: JSONObject): Map<String, Map<String, Int>> {
        val map = HashMap<String, Map<String, Int>>(json.length())
        for (key in json.keys()) {
            map[key] = jsonToMap(json.getJSONObject(key))
        }
        return map
    }
}
