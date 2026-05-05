package com.novaterm.core.session.manager

import android.content.Context
import android.util.Log
import com.novaterm.core.session.persistence.SessionGroup
import com.novaterm.core.session.persistence.SessionStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionGroupManagerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var sessionStore: SessionStore
    private lateinit var manager: SessionGroupManager
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0

        filesDir = tempDir.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        sessionStore = SessionStore(context)
        manager = SessionGroupManager(sessionStore, homeDir = "/data/data/com.nvterm/files/home")
    }

    // -- Initialization ------------------------------------------

    @Test
    fun `initialize loads built-in groups when no saved groups exist`() {
        manager.initialize()

        val groups = manager.groups.value
        assertTrue("Must have at least built-in groups", groups.isNotEmpty())
        assertNotNull("Must include shell group", groups.find { it.id == "shell" })
    }

    @Test
    fun `initialize loads saved groups`() {
        val customGroups = listOf(
            SessionGroup(id = "custom", name = "My Project", color = "#50FA7B"),
        )
        sessionStore.saveGroups(customGroups)

        manager.initialize()

        val groups = manager.groups.value
        assertEquals(1, groups.size)
        assertEquals("My Project", groups[0].name)
    }

    // -- Auto-detection -----------------------------------------

    @Test
    fun `detectGroupForCwd returns shell group for home directory`() {
        manager.initialize()

        val groupId = manager.detectGroupForCwd("/data/data/com.nvterm/files/home")

        assertEquals("shell", groupId)
    }

    @Test
    fun `detectGroupForCwd returns shell group for home with trailing slash`() {
        manager.initialize()

        val groupId = manager.detectGroupForCwd("/data/data/com.nvterm/files/home/")

        assertEquals("shell", groupId)
    }

    @Test
    fun `detectGroupForCwd creates project group for subdirectory`() {
        manager.initialize()

        val groupId = manager.detectGroupForCwd("/data/data/com.nvterm/files/home/projects/novaterm")

        assertNotNull("Group ID must not be null", groupId)
        assertTrue("Group must exist in groups list",
            manager.groups.value.any { it.id == groupId })
    }

    // -- Group management ----------------------------------------

    @Test
    fun `createGroup adds a new custom group`() {
        manager.initialize()

        val group = manager.createGroup(name = "Build Server", color = "#50FA7B", icon = "dns")

        assertEquals("Build Server", group.name)
        assertEquals("#50FA7B", group.color)
        assertEquals("dns", group.icon)
        assertFalse("Custom groups are not auto-created", group.isAutoCreated)

        assertTrue("Group must be in groups list",
            manager.groups.value.any { it.id == group.id })
    }

    @Test
    fun `renameGroup updates group name`() {
        manager.initialize()

        val group = manager.createGroup(name = "Old Name")
        manager.renameGroup(group.id, "New Name")

        val updated = manager.groups.value.find { it.id == group.id }
        assertEquals("New Name", updated?.name)
    }

    @Test
    fun `deleteGroup removes custom group and moves sessions to shell`() {
        manager.initialize()

        val group = manager.createGroup(name = "Temp Group")
        manager.assignSessionToGroup(0, group.id)
        manager.assignSessionToGroup(1, group.id)

        manager.deleteGroup(group.id)

        assertNull("Session 0 should be reassigned",
            manager.getGroupIdForSession(0)?.let { if (it != "shell") it else null })
    }

    @Test
    fun `deleteGroup does not delete built-in shell group`() {
        manager.initialize()

        val result = manager.deleteGroup("shell")

        assertFalse("Cannot delete built-in shell group", result)
        assertNotNull("Shell group must still exist",
            manager.groups.value.find { it.id == "shell" })
    }

    @Test
    fun `toggleGroupExpanded flips expansion state`() {
        manager.initialize()

        val shellGroup = manager.groups.value.first { it.id == "shell" }
        assertTrue("Shell group starts expanded", shellGroup.isExpanded)

        manager.toggleGroupExpanded("shell")

        val toggled = manager.groups.value.first { it.id == "shell" }
        assertFalse("Shell group should now be collapsed", toggled.isExpanded)
    }

    // -- Session -> Group mapping ---------------------------------

    @Test
    fun `assignSessionToGroup maps session to group`() {
        manager.initialize()

        val group = manager.createGroup(name = "Custom")

        manager.assignSessionToGroup(0, "shell")
        manager.assignSessionToGroup(1, group.id)

        assertEquals("shell", manager.getGroupIdForSession(0))
        assertEquals(group.id, manager.getGroupIdForSession(1))
    }

    @Test
    fun `moveSessionToGroup reassigns session`() {
        manager.initialize()

        val group = manager.createGroup(name = "Custom")

        manager.assignSessionToGroup(0, "shell")
        manager.moveSessionToGroup(0, group.id)

        assertEquals(group.id, manager.getGroupIdForSession(0))
    }

    @Test
    fun `unassignSession removes mapping`() {
        manager.initialize()

        manager.assignSessionToGroup(0, "shell")
        manager.unassignSession(0)

        assertNull(manager.getGroupIdForSession(0))
    }

    @Test
    fun `adjustSessionIndicesAfterRemoval shifts higher indices down`() {
        manager.initialize()

        val group = manager.createGroup(name = "Custom")

        manager.assignSessionToGroup(0, "shell")
        manager.assignSessionToGroup(1, "shell")
        manager.assignSessionToGroup(2, group.id)
        manager.assignSessionToGroup(3, group.id)

        // Remove session at index 1
        manager.adjustSessionIndicesAfterRemoval(1)

        // Session 0 stays, session 2->1, session 3->2
        assertEquals("shell", manager.getGroupIdForSession(0))
        assertEquals(group.id, manager.getGroupIdForSession(1))
        assertEquals(group.id, manager.getGroupIdForSession(2))
        assertNull("Removed index should not exist", manager.getGroupIdForSession(3))
    }

    @Test
    fun `getSessionsForGroup returns sessions in order`() {
        manager.initialize()

        val group = manager.createGroup(name = "Custom")

        manager.assignSessionToGroup(0, "shell")
        manager.assignSessionToGroup(2, "shell")
        manager.assignSessionToGroup(1, group.id)

        val shellSessions = manager.getSessionsForGroup("shell")
        assertEquals(listOf(0, 2), shellSessions)
    }

    @Test
    fun `getGroupsWithSessionCounts returns correct counts`() {
        manager.initialize()

        val group = manager.createGroup(name = "Custom")

        manager.assignSessionToGroup(0, "shell")
        manager.assignSessionToGroup(1, "shell")
        manager.assignSessionToGroup(2, group.id)

        val counts = manager.getGroupsWithSessionCounts()
        val shellCount = counts.find { it.group.id == "shell" }?.sessionCount
        val customCount = counts.find { it.group.id == group.id }?.sessionCount

        assertEquals(2, shellCount)
        assertEquals(1, customCount)
    }

    // -- Persistence ----------------------------------------------

    @Test
    fun `saveGroups persists groups and they can be reloaded`() {
        manager.initialize()
        manager.createGroup(name = "Test Project")

        manager.saveGroups()

        val newManager = SessionGroupManager(sessionStore,
            homeDir = "/data/data/com.nvterm/files/home")
        newManager.initialize()

        assertTrue("Reloaded groups must include Test Project",
            newManager.groups.value.any { it.name == "Test Project" })
    }

    // -- Settings toggle -----------------------------------------

    @Test
    fun `groupingEnabled defaults to true`() {
        assertTrue(manager.groupingEnabled.value)
    }

    @Test
    fun `setGroupingEnabled updates the state`() {
        manager.setGroupingEnabled(false)
        assertFalse(manager.groupingEnabled.value)

        manager.setGroupingEnabled(true)
        assertTrue(manager.groupingEnabled.value)
    }
}
