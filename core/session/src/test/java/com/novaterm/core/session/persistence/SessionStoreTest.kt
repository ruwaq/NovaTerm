package com.novaterm.core.session.persistence

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [SessionStore].
 *
 * Uses TemporaryFolder so every test operates on a real, isolated directory.
 * Context is mocked at the [Context.getFilesDir] boundary — the only Android
 * API surface [SessionStore] touches.
 *
 * No Android runtime required: pure JVM execution.
 */
class SessionStoreTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var store: SessionStore
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempDir.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        store = SessionStore(context)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun session(
        id: Int,
        shell: String = "/bin/bash",
        cwd: String = "/home",
        title: String = "shell",
        createdAt: Long = 1_000_000L,
    ) = SessionMetadata(id = id, shell = shell, cwd = cwd, title = title, createdAt = createdAt)

    private val sessionsFile: File get() = File(filesDir, "sessions.json")

    // -------------------------------------------------------------------------
    // 1. save() writes file, load() reads it back correctly
    // -------------------------------------------------------------------------

    @Test
    fun `save writes json file and load returns identical sessions`() {
        val sessions = listOf(
            session(id = 0, shell = "/bin/bash", cwd = "/home/projects", title = "editor"),
            session(id = 1, shell = "/bin/zsh",  cwd = "/tmp",           title = "scratch"),
        )

        store.save(sessions)

        assertTrue("sessions.json must exist after save()", sessionsFile.exists())
        val restored = store.load()
        assertEquals(2, restored.size)

        assertEquals(0,               restored[0].id)
        assertEquals("/bin/bash",     restored[0].shell)
        assertEquals("/home/projects",restored[0].cwd)
        assertEquals("editor",        restored[0].title)

        assertEquals(1,         restored[1].id)
        assertEquals("/bin/zsh",restored[1].shell)
        assertEquals("/tmp",    restored[1].cwd)
        assertEquals("scratch", restored[1].title)
    }

    // -------------------------------------------------------------------------
    // 2. load() returns empty list when file doesn't exist
    // -------------------------------------------------------------------------

    @Test
    fun `load returns empty list when sessions file does not exist`() {
        assertFalse("Pre-condition: file must not exist", sessionsFile.exists())

        val result = store.load()

        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 3. load() returns empty list on invalid JSON
    // -------------------------------------------------------------------------

    @Test
    fun `load returns empty list when file contains invalid json`() {
        sessionsFile.writeText("{not valid json at all!!!")

        val result = store.load()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `load returns empty list when file contains only whitespace`() {
        sessionsFile.writeText("   \n\t  ")

        val result = store.load()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `load returns empty list when file contains a json object instead of array`() {
        sessionsFile.writeText("""{"id":0,"shell":"/bin/bash"}""")

        val result = store.load()

        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 4. clear() deletes the file
    // -------------------------------------------------------------------------

    @Test
    fun `clear deletes the sessions file`() {
        store.save(listOf(session(id = 0)))
        assertTrue("Pre-condition: file must exist before clear()", sessionsFile.exists())

        store.clear()

        assertFalse("sessions.json must be deleted after clear()", sessionsFile.exists())
    }

    @Test
    fun `clear is a no-op when file does not exist`() {
        assertFalse("Pre-condition: file must not exist", sessionsFile.exists())

        // Must not throw
        store.clear()

        assertFalse(sessionsFile.exists())
    }

    // -------------------------------------------------------------------------
    // 5. save() with empty list creates valid JSON
    // -------------------------------------------------------------------------

    @Test
    fun `save with empty list writes a valid empty json array`() {
        store.save(emptyList())

        assertTrue("File must be written even for an empty session list", sessionsFile.exists())
        val content = sessionsFile.readText().trim()
        assertTrue("Content must be a valid JSON array", content.startsWith("["))
        assertTrue("Content must be a valid JSON array", content.endsWith("]"))

        val reloaded = store.load()
        assertTrue("load() after save([]) must return empty list", reloaded.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 6. Concurrent save/load doesn't corrupt data
    // -------------------------------------------------------------------------

    @Test
    fun `concurrent save and load do not corrupt data`() {
        val threadCount = 8
        val iterationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch  = CountDownLatch(threadCount)
        val errors = mutableListOf<Throwable>()

        val writers = (0 until threadCount / 2).map { writerIdx ->
            executor.submit {
                try {
                    startLatch.await()
                    repeat(iterationsPerThread) { i ->
                        val data = (0..writerIdx).map { session(id = it, title = "w$writerIdx-i$i") }
                        store.save(data)
                    }
                } catch (t: Throwable) {
                    synchronized(errors) { errors += t }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        val readers = (0 until threadCount / 2).map {
            executor.submit {
                try {
                    startLatch.await()
                    repeat(iterationsPerThread) {
                        // load() must never throw — it must return a list (possibly empty)
                        val loaded = store.load()
                        // Each entry must have a non-negative id (i.e. not corrupted zeros from a torn write)
                        loaded.forEach { meta ->
                            assertTrue("id must be non-negative", meta.id >= 0)
                            assertTrue("shell must not be empty", meta.shell.isNotEmpty())
                        }
                    }
                } catch (t: Throwable) {
                    synchronized(errors) { errors += t }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown() // release all threads at once
        val finished = doneLatch.await(10, TimeUnit.SECONDS)

        executor.shutdown()
        assertTrue("All threads must finish within timeout", finished)
        assertTrue("No errors expected during concurrent access: $errors", errors.isEmpty())

        // Suppress unused variable warnings
        writers.forEach { it.get() }
        readers.forEach { it.get() }
    }

    // -------------------------------------------------------------------------
    // 7. SessionMetadata fields are correctly serialized/deserialized
    // -------------------------------------------------------------------------

    @Test
    fun `all SessionMetadata fields survive a save-load round-trip`() {
        val original = SessionMetadata(
            id        = 42,
            shell     = "/data/data/com.nvterm/files/usr/bin/zsh",
            cwd       = "/data/data/com.nvterm/files/home/projects/novaterm",
            title     = "NovaTerm Dev",
            createdAt = 1_712_345_678_000L,
        )

        store.save(listOf(original))
        val restored = store.load().single()

        assertEquals(original.id,        restored.id)
        assertEquals(original.shell,     restored.shell)
        assertEquals(original.cwd,       restored.cwd)
        assertEquals(original.title,     restored.title)
        assertEquals(original.createdAt, restored.createdAt)
    }

    @Test
    fun `title with special characters survives round-trip`() {
        val meta = session(id = 0, title = "nano — README.md · ~/projects")
        store.save(listOf(meta))
        val restored = store.load().single()
        assertEquals(meta.title, restored.title)
    }

    @Test
    fun `cwd with spaces and unicode survives round-trip`() {
        val meta = session(id = 0, cwd = "/home/user/My Documents/código")
        store.save(listOf(meta))
        val restored = store.load().single()
        assertEquals(meta.cwd, restored.cwd)
    }

    @Test
    fun `save followed by clear followed by load returns empty list`() {
        store.save(listOf(session(id = 0), session(id = 1)))
        store.clear()
        val result = store.load()
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 8. hasSavedSessions() reflects file state
    // -------------------------------------------------------------------------

    @Test
    fun `hasSavedSessions returns false when no file exists`() {
        assertFalse(store.hasSavedSessions())
    }

    @Test
    fun `hasSavedSessions returns false after saving empty list`() {
        store.save(emptyList())
        assertFalse("An empty JSON array should not count as saved sessions", store.hasSavedSessions())
    }

    @Test
    fun `hasSavedSessions returns true after saving non-empty list`() {
        store.save(listOf(session(id = 0)))
        assertTrue(store.hasSavedSessions())
    }

    @Test
    fun `hasSavedSessions returns false after clear`() {
        store.save(listOf(session(id = 0)))
        store.clear()
        assertFalse(store.hasSavedSessions())
    }

    // -------------------------------------------------------------------------
    // 9. Overwrite semantics — second save replaces first
    // -------------------------------------------------------------------------

    @Test
    fun `second save completely replaces first save`() {
        store.save(listOf(session(id = 0, title = "alpha"), session(id = 1, title = "beta")))
        store.save(listOf(session(id = 99, title = "gamma")))

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(99,      loaded[0].id)
        assertEquals("gamma", loaded[0].title)
    }

    // -------------------------------------------------------------------------
    // 10. Large session list round-trip (boundary / stress)
    // -------------------------------------------------------------------------

    @Test
    fun `large session list serializes and deserializes correctly`() {
        val count = 500
        val sessions = (0 until count).map { i ->
            session(id = i, shell = "/bin/bash", cwd = "/home/user/project-$i", title = "tab $i")
        }

        store.save(sessions)
        val loaded = store.load()

        assertEquals(count, loaded.size)
        loaded.forEachIndexed { index, meta ->
            assertEquals(index,            meta.id)
            assertEquals("tab $index",     meta.title)
            assertEquals("/home/user/project-$index", meta.cwd)
        }
    }

    // -------------------------------------------------------------------------
    // Group persistence tests
    // -------------------------------------------------------------------------

    private val groupsFile: File get() = File(filesDir, "session_groups.json")

    @Test
    fun `saveGroups and loadGroups round-trip`() {
        val groups = listOf(
            SessionGroup(id = "shell", name = "Shell", color = "#E85D04", icon = "terminal"),
            SessionGroup(id = "agents", name = "AI Agents", color = "#8BE9FD", icon = "smart_toy"),
        )

        store.saveGroups(groups)
        assertTrue("session_groups.json must exist after saveGroups()", groupsFile.exists())

        val restored = store.loadGroups()
        assertEquals(2, restored.size)
        assertEquals("Shell", restored[0].name)
        assertEquals("agents", restored[1].id)
    }

    @Test
    fun `loadGroups returns built-ins when no file exists`() {
        assertFalse("Pre-condition: file must not exist", groupsFile.exists())

        val groups = store.loadGroups()

        assertTrue("Built-in groups must be returned when no file exists", groups.isNotEmpty())
        assertNotNull("Must include 'shell' group", groups.find { it.id == "shell" })
    }

    @Test
    fun `loadGroups returns built-ins when file contains invalid json`() {
        groupsFile.writeText("{broken")

        val groups = store.loadGroups()

        assertTrue("Built-in groups on invalid JSON", groups.isNotEmpty())
    }

    @Test
    fun `clearGroups deletes the groups file`() {
        store.saveGroups(SessionGroup.BUILT_INS)
        assertTrue("Pre-condition: file must exist before clearGroups()", groupsFile.exists())

        store.clearGroups()

        assertFalse("session_groups.json must be deleted after clearGroups()", groupsFile.exists())
    }

    @Test
    fun `all SessionGroup fields survive a save-load round-trip`() {
        val original = SessionGroup(
            id = "test", name = "My Group", color = "#50FA7B",
            icon = "code", isExpanded = false, sortOrder = 5,
            isAutoCreated = true, createdAt = 1_712_345_678_000L,
        )

        store.saveGroups(listOf(original))
        val restored = store.loadGroups().single()

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.color, restored.color)
        assertEquals(original.icon, restored.icon)
        assertEquals(original.isExpanded, restored.isExpanded)
        assertEquals(original.sortOrder, restored.sortOrder)
        assertEquals(original.isAutoCreated, restored.isAutoCreated)
        assertEquals(original.createdAt, restored.createdAt)
    }
}
