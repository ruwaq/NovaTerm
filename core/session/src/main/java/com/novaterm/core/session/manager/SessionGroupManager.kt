package com.novaterm.core.session.manager

import android.util.Log
import com.novaterm.core.session.persistence.SessionGroup
import com.novaterm.core.session.persistence.SessionGroupSerializer
import com.novaterm.core.session.persistence.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Manages session groups — the organizational layer above individual sessions.
 *
 * Inspired by Superset IDE workspaces and cmux named workspace groups:
 * - Sessions are auto-grouped by project (git root of CWD) or agent type
 * - Users can manually create, rename, and reorganize groups
 * - Groups persist across app restarts via [SessionStore]
 *
 * Lifecycle:
 * 1. On startup, load groups from [SessionStore]
 * 2. When a session is created, auto-assign it to a group via [detectGroupForCwd]
 * 3. When an agent workspace is created, assign it via [detectGroupForAgent]
 * 4. User can move sessions between groups via [moveSessionToGroup]
 * 5. Groups are saved periodically and on service destroy
 */
class SessionGroupManager(
    private val sessionStore: SessionStore,
    private val homeDir: String,
) {
    companion object {
        private const val TAG = "SessionGroupManager"
    }

    /** All groups, sorted by sortOrder. Reactive for Compose observation. */
    private val _groups = MutableStateFlow<List<SessionGroup>>(emptyList())
    val groups: StateFlow<List<SessionGroup>> = _groups.asStateFlow()

    /** Maps session index → group ID. Reactive for Compose observation. */
    private val _sessionGroupMap = MutableStateFlow<Map<Int, String>>(emptyMap())
    val sessionGroupMap: StateFlow<Map<Int, String>> = _sessionGroupMap.asStateFlow()

    /** Whether session grouping is enabled in Settings. */
    private val _groupingEnabled = MutableStateFlow(true)
    val groupingEnabled: StateFlow<Boolean> = _groupingEnabled.asStateFlow()

    // ── Initialization ──────────────────────────────────────────

    /**
     * Load groups from persistent storage. Call once on service startup.
     * Falls back to built-in groups if none are saved.
     */
    fun initialize() {
        val saved = sessionStore.loadGroups()
        _groups.value = saved.sortedBy { it.sortOrder }
        Log.i(TAG, "Loaded ${saved.size} session groups")
    }

    /**
     * Save current groups to persistent storage. Call on service destroy
     * and periodically (every 30s alongside session metadata save).
     */
    fun saveGroups() {
        sessionStore.saveGroups(_groups.value)
    }

    // ── Auto-detection ─────────────────────────────────────────

    /**
     * Detect the appropriate group for a session based on its CWD.
     *
     * Strategy:
     * 1. If CWD is the home directory → default "Shell" group
     * 2. If CWD is inside a git repository → project group (named after dir name)
     * 3. If CWD is a subdirectory of home → project group (named after first subdirectory)
     * 4. Fallback → "Shell" group
     *
     * Returns the group ID of the matched or newly created group.
     */
    fun detectGroupForCwd(cwd: String): String {
        // Home directory → default shell group
        if (cwd == homeDir || cwd == "$homeDir/") {
            return getOrCreateShellGroup().id
        }

        // Try to detect git root → project group
        val projectDir = detectProjectDir(cwd)
        if (projectDir != null) {
            return getOrCreateGroupForProject(projectDir).id
        }

        // CWD is a subdirectory of home but not a git repo → use first subdirectory
        if (cwd.startsWith(homeDir)) {
            val relative = cwd.removePrefix(homeDir).removePrefix("/")
            val firstDir = relative.substringBefore("/")
            if (firstDir.isNotBlank()) {
                return getOrCreateGroupForProject(firstDir).id
            }
        }

        // Fallback to shell group
        return getOrCreateShellGroup().id
    }

    /**
     * Detect the appropriate group for an agent workspace.
     * Maps agent types to their dedicated groups.
     */
    fun detectGroupForAgent(agentType: AgentType): String {
        return getOrCreateAgentGroup(agentType).id
    }

    // ── Group management ────────────────────────────────────────

    /**
     * Create a new custom group. Returns the created group.
     */
    fun createGroup(
        name: String,
        color: String = SessionGroup.DEFAULT_COLOR,
        icon: String = SessionGroup.DEFAULT_ICON,
    ): SessionGroup {
        val maxSort = _groups.value.maxOfOrNull { it.sortOrder } ?: -1
        val group = SessionGroup(
            name = name,
            color = color,
            icon = icon,
            sortOrder = maxSort + 10,
            isAutoCreated = false,
        )
        _groups.value = (_groups.value + group).sortedBy { it.sortOrder }
        saveGroups()
        Log.i(TAG, "Created group: ${group.id} → ${group.name}")
        return group
    }

    /**
     * Rename an existing group.
     */
    fun renameGroup(groupId: String, newName: String) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) group.copy(name = newName) else group
        }
        saveGroups()
    }

    /**
     * Update a group's color.
     */
    fun updateGroupColor(groupId: String, color: String) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) group.copy(color = color) else group
        }
        saveGroups()
    }

    /**
     * Update a group's icon.
     */
    fun updateGroupIcon(groupId: String, icon: String) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) group.copy(icon = icon) else group
        }
        saveGroups()
    }

    /**
     * Delete a group. Sessions in this group are moved to the default group.
     * Built-in groups (shell, agents) cannot be deleted.
     */
    fun deleteGroup(groupId: String): Boolean {
        val group = _groups.value.find { it.id == groupId } ?: return false
        if (group.isAutoCreated && group.id in listOf("shell", "agents")) {
            Log.w(TAG, "Cannot delete built-in group: ${group.id}")
            return false
        }

        // Move sessions from deleted group to shell group
        val shellGroupId = getOrCreateShellGroup().id
        _sessionGroupMap.value = _sessionGroupMap.value.mapValues { (_, gid) ->
            if (gid == groupId) shellGroupId else gid
        }

        _groups.value = _groups.value.filter { it.id != groupId }
        saveGroups()
        Log.i(TAG, "Deleted group: $groupId")
        return true
    }

    /**
     * Toggle a group's expanded/collapsed state in the tab bar.
     */
    fun toggleGroupExpanded(groupId: String) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) group.copy(isExpanded = !group.isExpanded) else group
        }
        saveGroups()
    }

    /**
     * Reorder groups. Pass the full list in desired display order.
     */
    fun reorderGroups(groupsInOrder: List<SessionGroup>) {
        val reordered = groupsInOrder.mapIndexed { index, group ->
            group.copy(sortOrder = index * 10)
        }
        _groups.value = reordered.sortedBy { it.sortOrder }
        saveGroups()
    }

    // ── Session → Group mapping ─────────────────────────────────

    /**
     * Assign a session to a group.
     */
    fun assignSessionToGroup(sessionIndex: Int, groupId: String) {
        _sessionGroupMap.value = _sessionGroupMap.value + (sessionIndex to groupId)
    }

    /**
     * Move a session from its current group to a different group.
     */
    fun moveSessionToGroup(sessionIndex: Int, targetGroupId: String) {
        assignSessionToGroup(sessionIndex, targetGroupId)
    }

    /**
     * Remove a session's group assignment (e.g. when session is destroyed).
     */
    fun unassignSession(sessionIndex: Int) {
        _sessionGroupMap.value = _sessionGroupMap.value - sessionIndex
    }

    /**
     * Adjust all session indices after a session is removed.
     * All sessions with index > removedIndex get decremented by 1.
     */
    fun adjustSessionIndicesAfterRemoval(removedIndex: Int) {
        val oldMap = _sessionGroupMap.value.toMutableMap()
        val newMap = mutableMapOf<Int, String>()
        for ((index, groupId) in oldMap) {
            when {
                index < removedIndex -> newMap[index] = groupId
                index > removedIndex -> newMap[index - 1] = groupId
                // index == removedIndex: dropped
            }
        }
        _sessionGroupMap.value = newMap
    }

    /**
     * Get the group ID for a session, or null if unassigned.
     */
    fun getGroupIdForSession(sessionIndex: Int): String? {
        return _sessionGroupMap.value[sessionIndex]
    }

    /**
     * Get the group for a session, falling back to the default group.
     */
    fun getGroupForSession(sessionIndex: Int): SessionGroup {
        val groupId = _sessionGroupMap.value[sessionIndex]
        return _groups.value.find { it.id == groupId } ?: getOrCreateShellGroup()
    }

    /**
     * Get all sessions belonging to a group, in order.
     */
    fun getSessionsForGroup(groupId: String): List<Int> {
        return _sessionGroupMap.value
            .filter { (_, gid) -> gid == groupId }
            .keys
            .sorted()
    }

    /**
     * Get groups ordered for display, with session counts.
     */
    fun getGroupsWithSessionCounts(): List<GroupWithCount> {
        val sessionMap = _sessionGroupMap.value
        return _groups.value.map { group ->
            val count = sessionMap.values.count { it == group.id }
            GroupWithCount(group, count)
        }
    }

    // ── Settings ────────────────────────────────────────────────

    fun setGroupingEnabled(enabled: Boolean) {
        _groupingEnabled.value = enabled
    }

    // ── Private helpers ──────────────────────────────────────────

    private fun getOrCreateShellGroup(): SessionGroup {
        return _groups.value.find { it.id == "shell" } ?: run {
            val group = SessionGroup.BUILT_INS.first { it.id == "shell" }
            _groups.value = (_groups.value + group).sortedBy { it.sortOrder }
            group
        }
    }

    private fun getOrCreateAgentGroup(agentType: AgentType): SessionGroup {
        // Try to find existing "AI Agents" group first
        val existing = _groups.value.find { it.id == "agents" }
        if (existing != null) return existing

        // Create the built-in agents group
        val group = SessionGroup.BUILT_INS.first { it.id == "agents" }
        _groups.value = (_groups.value + group).sortedBy { it.sortOrder }
        return group
    }

    private fun getOrCreateGroupForProject(projectName: String): SessionGroup {
        // Try to find an existing group with this name
        val existing = _groups.value.find {
            it.name.equals(projectName, ignoreCase = true) && it.isAutoCreated
        }
        if (existing != null) return existing

        // Create a new auto-detected project group
        val group = SessionGroup.forProject(projectName)
        _groups.value = (_groups.value + group).sortedBy { it.sortOrder }
        saveGroups()
        return group
    }

    /**
     * Detect the project directory from a CWD path.
     * Looks for .git directory to identify project roots.
     * Returns the project directory name if found, null otherwise.
     */
    private fun detectProjectDir(cwd: String): String? {
        var dir = File(cwd)
        // Walk up the directory tree looking for .git
        var depth = 0
        while (dir.absolutePath != homeDir && dir.absolutePath != "/" && depth < 10) {
            if (File(dir, ".git").exists()) {
                return dir.name
            }
            dir = dir.parentFile ?: break
            depth++
        }
        return null
    }
}

/**
 * A group with its session count, for UI display.
 */
data class GroupWithCount(
    val group: SessionGroup,
    val sessionCount: Int,
)