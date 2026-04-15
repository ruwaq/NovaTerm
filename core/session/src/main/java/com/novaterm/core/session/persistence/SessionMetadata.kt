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
    val activeTabIndex: Int = 0,
    val tabNames: Map<Int, String> = emptyMap(),
    val focusedPaneSession: Int = -1,
    /** Group ID linking this session to a [SessionGroup]. Null = default group. */
    val groupId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("shell", shell)
        put("cwd", cwd)
        put("title", title)
        put("activeTabIndex", activeTabIndex)
        put("tabNames", JSONObject(tabNames.mapValues { (_, name) -> name }))
        put("focusedPaneSession", focusedPaneSession)
        put("groupId", groupId ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): SessionMetadata = SessionMetadata(
            id = json.getInt("id"),
            shell = json.getString("shell"),
            cwd = json.getString("cwd"),
            title = json.optString("title", "shell"),
            activeTabIndex = json.optInt("activeTabIndex", 0),
            tabNames = json.optJSONObject("tabNames")?.let { obj ->
                val map = mutableMapOf<Int, String>()
                for (key in obj.keys()) {
                    key.toIntOrNull()?.let { k -> map[k] = obj.getString(key) }
                }
                map
            } ?: emptyMap(),
            focusedPaneSession = json.optInt("focusedPaneSession", -1),
            groupId = json.opt("groupId")?.takeIf { it !== JSONObject.NULL } as? String,
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
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
