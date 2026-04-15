package com.novaterm.core.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMetadataTest {

    @Test
    fun `serialize and deserialize round-trip`() {
        val sessions = listOf(
            SessionMetadata(id = 0, shell = "/bin/bash", cwd = "/home/projects", title = "editor"),
            SessionMetadata(id = 1, shell = "/bin/zsh", cwd = "/home", title = "shell"),
        )

        val json = SessionMetadataSerializer.serialize(sessions)
        val restored = SessionMetadataSerializer.deserialize(json)

        assertEquals(2, restored.size)
        assertEquals("editor", restored[0].title)
        assertEquals("/home/projects", restored[0].cwd)
        assertEquals("/bin/zsh", restored[1].shell)
    }

    @Test
    fun `deserialize empty string returns empty list`() {
        assertEquals(emptyList<SessionMetadata>(), SessionMetadataSerializer.deserialize(""))
    }

    @Test
    fun `deserialize invalid json returns empty list`() {
        assertEquals(emptyList<SessionMetadata>(), SessionMetadataSerializer.deserialize("{broken"))
    }

    @Test
    fun `toJson includes all fields`() {
        val meta = SessionMetadata(id = 5, shell = "/bin/sh", cwd = "/tmp", title = "test", createdAt = 1000L)
        val json = meta.toJson()

        assertEquals(5, json.getInt("id"))
        assertEquals("/bin/sh", json.getString("shell"))
        assertEquals("/tmp", json.getString("cwd"))
        assertEquals("test", json.getString("title"))
        assertEquals(1000L, json.getLong("createdAt"))
    }

    @Test
    fun `fromJson handles missing title gracefully`() {
        val json = org.json.JSONObject().apply {
            put("id", 1)
            put("shell", "/bin/sh")
            put("cwd", "/home")
        }
        val meta = SessionMetadata.fromJson(json)
        assertEquals("shell", meta.title) // default
    }

    @Test
    fun `groupId round-trip through JSON`() {
        val meta = SessionMetadata(
            id = 3, shell = "/bin/bash", cwd = "/home/project",
            title = "dev", groupId = "novaterm",
        )
        val json = meta.toJson()
        assertEquals("novaterm", json.getString("groupId"))

        val restored = SessionMetadata.fromJson(json)
        assertEquals("novaterm", restored.groupId)
    }

    @Test
    fun `groupId null by default and round-trips as JSON NULL`() {
        val meta = SessionMetadata(
            id = 0, shell = "/bin/bash", cwd = "/home", title = "shell",
        )
        assertNull(meta.groupId)

        val json = meta.toJson()
        // groupId should be JSONObject.NULL when null
        assertTrue(json.isNull("groupId"))

        val restored = SessionMetadata.fromJson(json)
        assertNull(restored.groupId)
    }

    @Test
    fun `groupId preserved in full serialize-deserialize cycle`() {
        val sessions = listOf(
            SessionMetadata(id = 0, shell = "/bin/bash", cwd = "/home/project",
                title = "dev", groupId = "project-alpha"),
            SessionMetadata(id = 1, shell = "/bin/zsh", cwd = "/home",
                title = "shell", groupId = null),
            SessionMetadata(id = 2, shell = "/bin/bash", cwd = "/home/agent",
                title = "claude", groupId = "agents"),
        )

        val json = SessionMetadataSerializer.serialize(sessions)
        val restored = SessionMetadataSerializer.deserialize(json)

        assertEquals("project-alpha", restored[0].groupId)
        assertNull(restored[1].groupId)
        assertEquals("agents", restored[2].groupId)
    }

    @Test
    fun `serialize empty list produces valid json`() {
        val json = SessionMetadataSerializer.serialize(emptyList())
        assertTrue(json.contains("[]") || json.trim() == "[\n\n]" || json.trim() == "[ ]")
        val restored = SessionMetadataSerializer.deserialize(json)
        assertTrue(restored.isEmpty())
    }
}
