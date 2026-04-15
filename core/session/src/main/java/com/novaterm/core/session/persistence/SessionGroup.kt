package com.novaterm.core.session.persistence

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A named group that contains related terminal sessions.
 *
 * Inspired by Superset IDE workspaces and cmux named workspace groups.
 * Sessions are auto-grouped by project (git root of CWD) or agent type,
 * but users can also manually create and organize groups.
 *
 * @property id Unique identifier (8-char UUID prefix for readability).
 * @property name Human-readable group name (e.g. "NovaTerm", "AI Agents", "Shell").
 * @property color Accent color in hex (e.g. "#E85D04"). Used for group header
 *   and status dot tinting in the tab bar.
 * @property icon Material icon key for the group header. Default: "folder".
 * @property isExpanded Whether the group is expanded in the tab bar.
 * @property sortOrder Display order — lower values appear first.
 * @property isAutoCreated True if this group was auto-created from CWD/agent detection.
 *   Auto-created groups can be renamed but won't be deleted even if empty
 *   (they'll be reused when a matching session appears).
 * @property createdAt Creation timestamp for ordering.
 */
data class SessionGroup(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val color: String = DEFAULT_COLOR,
    val icon: String = DEFAULT_ICON,
    val isExpanded: Boolean = true,
    val sortOrder: Int = 0,
    val isAutoCreated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("icon", icon)
        put("isExpanded", isExpanded)
        put("sortOrder", sortOrder)
        put("isAutoCreated", isAutoCreated)
        put("createdAt", createdAt)
    }

    companion object {
        /** Default accent color — Ember orange, matching NovaTerm theme. */
        const val DEFAULT_COLOR = "#E85D04"
        const val DEFAULT_ICON = "folder"

        /** Predefined colors for quick group creation. */
        val GROUP_COLORS = listOf(
            "#E85D04" to "Ember",    // Default
            "#50FA7B" to "Green",    // Running/success
            "#FF5555" to "Red",      // Error/alert
            "#8BE9FD" to "Cyan",     // Info/agent
            "#F1FA8C" to "Yellow",   // Warning
            "#BD93F9" to "Purple",   // Special
            "#FFB86C" to "Orange",   // Secondary
            "#FF79C6" to "Pink",     // Accent
        )

        /** Predefined icons for groups. */
        val GROUP_ICONS = listOf(
            "folder" to "Folder",
            "terminal" to "Terminal",
            "smart_toy" to "AI Agent",
            "code" to "Code",
            "build" to "Build",
            "bug_report" to "Debug",
            "storage" to "Server",
            "dns" to "Infrastructure",
        )

        fun fromJson(json: JSONObject): SessionGroup = SessionGroup(
            id = json.optString("id", UUID.randomUUID().toString().take(8)),
            name = json.optString("name", "Sessions"),
            color = json.optString("color", DEFAULT_COLOR),
            icon = json.optString("icon", DEFAULT_ICON),
            isExpanded = json.optBoolean("isExpanded", true),
            sortOrder = json.optInt("sortOrder", 0),
            isAutoCreated = json.optBoolean("isAutoCreated", false),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        )

        /** Built-in groups that are always available. */
        val BUILT_INS: List<SessionGroup> = listOf(
            SessionGroup(
                id = "shell",
                name = "Shell",
                color = "#E85D04",
                icon = "terminal",
                sortOrder = 0,
                isAutoCreated = true,
            ),
            SessionGroup(
                id = "agents",
                name = "AI Agents",
                color = "#8BE9FD",
                icon = "smart_toy",
                sortOrder = 100,
                isAutoCreated = true,
            ),
        )

        /** Find or create a group for a project directory.
         *  Uses the last directory name as the group name.
         */
        fun forProject(projectDir: String): SessionGroup {
            val dirName = projectDir.substringAfterLast('/').ifBlank { "Project" }
            return SessionGroup(
                name = dirName,
                color = "#BD93F9",  // Purple for projects
                icon = "code",
                sortOrder = 50,
                isAutoCreated = true,
            )
        }

        /** Find or create a group for an agent type. */
        fun forAgentType(agentLabel: String): SessionGroup {
            return SessionGroup(
                name = agentLabel,
                color = "#8BE9FD",  // Cyan for agents
                icon = "smart_toy",
                sortOrder = 100,
                isAutoCreated = true,
            )
        }
    }
}

/**
 * Reads/writes a list of [SessionGroup] to a JSON file.
 */
object SessionGroupSerializer {

    fun serialize(groups: List<SessionGroup>): String {
        val array = JSONArray()
        groups.forEach { array.put(it.toJson()) }
        return array.toString(2)
    }

    fun deserialize(json: String): List<SessionGroup> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { SessionGroup.fromJson(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}