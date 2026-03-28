package com.novaterm.core.session.persistence

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializable metadata for a terminal session.
 * Persisted to disk so sessions can be recreated after process death.
 *
 * Note: The actual shell process and PTY cannot be restored — only the
 * working directory, title, and shell path are saved so a new session
 * can be opened in the same place.
 */
data class SessionMetadata(
    val id: Int,
    val shell: String,
    val cwd: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("shell", shell)
        put("cwd", cwd)
        put("title", title)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): SessionMetadata = SessionMetadata(
            id = json.getInt("id"),
            shell = json.getString("shell"),
            cwd = json.getString("cwd"),
            title = json.optString("title", "shell"),
            createdAt = json.optLong("createdAt", 0),
        )
    }
}

/**
 * Reads/writes a list of [SessionMetadata] to a JSON file.
 */
object SessionMetadataSerializer {

    fun serialize(sessions: List<SessionMetadata>): String {
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        return array.toString(2)
    }

    fun deserialize(json: String): List<SessionMetadata> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { SessionMetadata.fromJson(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
