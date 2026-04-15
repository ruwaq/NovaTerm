package com.novaterm.core.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGroupTest {

    @Test
    fun `serialize and deserialize round-trip`() {
        val groups = listOf(
            SessionGroup(id = "shell", name = "Shell", color = "#E85D04", icon = "terminal"),
            SessionGroup(id = "agents", name = "AI Agents", color = "#8BE9FD", icon = "smart_toy"),
        )

        val json = SessionGroupSerializer.serialize(groups)
        val restored = SessionGroupSerializer.deserialize(json)

        assertEquals(2, restored.size)
        assertEquals("Shell", restored[0].name)
        assertEquals("#E85D04", restored[0].color)
        assertEquals("agents", restored[1].id)
        assertEquals("smart_toy", restored[1].icon)
    }

    @Test
    fun `deserialize empty string returns empty list`() {
        assertEquals(emptyList<SessionGroup>(), SessionGroupSerializer.deserialize(""))
    }

    @Test
    fun `deserialize invalid json returns empty list`() {
        assertEquals(emptyList<SessionGroup>(), SessionGroupSerializer.deserialize("{broken"))
    }

    @Test
    fun `toJson includes all fields`() {
        val group = SessionGroup(
            id = "test", name = "My Group", color = "#50FA7B",
            icon = "code", isExpanded = false, sortOrder = 5,
            isAutoCreated = true, createdAt = 1000L,
        )
        val json = group.toJson()

        assertEquals("test", json.getString("id"))
        assertEquals("My Group", json.getString("name"))
        assertEquals("#50FA7B", json.getString("color"))
        assertEquals("code", json.getString("icon"))
        assertEquals(false, json.getBoolean("isExpanded"))
        assertEquals(5, json.getInt("sortOrder"))
        assertEquals(true, json.getBoolean("isAutoCreated"))
        assertEquals(1000L, json.getLong("createdAt"))
    }

    @Test
    fun `fromJson handles missing optional fields with defaults`() {
        val json = org.json.JSONObject().apply {
            put("id", "abc123")
            put("name", "Minimal")
        }
        val group = SessionGroup.fromJson(json)

        assertEquals("abc123", group.id)
        assertEquals("Minimal", group.name)
        assertEquals(SessionGroup.DEFAULT_COLOR, group.color)
        assertEquals(SessionGroup.DEFAULT_ICON, group.icon)
        assertTrue(group.isExpanded)
        assertEquals(0, group.sortOrder)
        assertNull(group.isAutoCreated?.let { if (!it) null else true }) // false by default
    }

    @Test
    fun `built-in groups have expected values`() {
        val builtIns = SessionGroup.BUILT_INS
        assertTrue(builtIns.size >= 2)

        val shell = builtIns.first { it.id == "shell" }
        assertEquals("Shell", shell.name)
        assertEquals("terminal", shell.icon)
        assertTrue(shell.isAutoCreated)

        val agents = builtIns.first { it.id == "agents" }
        assertEquals("AI Agents", agents.name)
        assertEquals("smart_toy", agents.icon)
        assertTrue(agents.isAutoCreated)
    }

    @Test
    fun `forProject creates group with directory name`() {
        val group = SessionGroup.forProject("/home/user/projects/novaterm")
        assertEquals("novaterm", group.name)
        assertEquals("code", group.icon)
        assertTrue(group.isAutoCreated)
    }

    @Test
    fun `forAgentType creates group with agent label`() {
        val group = SessionGroup.forAgentType("Claude Code")
        assertEquals("Claude Code", group.name)
        assertEquals("smart_toy", group.icon)
        assertTrue(group.isAutoCreated)
    }

    @Test
    fun `group colors and icons lists are not empty`() {
        assertTrue(SessionGroup.GROUP_COLORS.isNotEmpty())
        assertTrue(SessionGroup.GROUP_ICONS.isNotEmpty())
    }
}