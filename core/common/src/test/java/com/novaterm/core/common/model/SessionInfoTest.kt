package com.novaterm.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionInfoTest {

    @Test
    fun `running session has no exit code`() {
        val session = SessionInfo(
            id = 1,
            pid = 12345,
            title = "bash",
            cwd = "/home/user",
            status = SessionStatus.RUNNING,
        )
        assertEquals(SessionStatus.RUNNING, session.status)
        assertNull(session.exitCode)
    }

    @Test
    fun `finished session has exit code`() {
        val session = SessionInfo(
            id = 2,
            pid = 12346,
            title = "python",
            cwd = "/home/user/project",
            status = SessionStatus.FINISHED,
            exitCode = 0,
        )
        assertEquals(SessionStatus.FINISHED, session.status)
        assertEquals(0, session.exitCode)
    }

    @Test
    fun `crashed session has non-zero exit code`() {
        val session = SessionInfo(
            id = 3,
            pid = 12347,
            title = "node",
            cwd = "/tmp",
            status = SessionStatus.CRASHED,
            exitCode = 137,
        )
        assertEquals(SessionStatus.CRASHED, session.status)
        assertEquals(137, session.exitCode)
    }

    @Test
    fun `copy updates only specified fields`() {
        val original = SessionInfo(
            id = 1, pid = 100, title = "bash", cwd = "/home",
            status = SessionStatus.RUNNING,
        )
        val updated = original.copy(
            title = "zsh",
            cwd = "/home/projects",
        )
        assertEquals(1, updated.id)
        assertEquals(100, updated.pid)
        assertEquals("zsh", updated.title)
        assertEquals("/home/projects", updated.cwd)
        assertEquals(SessionStatus.RUNNING, updated.status)
    }

    @Test
    fun `all session statuses are enumerable`() {
        val statuses = SessionStatus.entries
        assertEquals(3, statuses.size)
        assert(SessionStatus.RUNNING in statuses)
        assert(SessionStatus.FINISHED in statuses)
        assert(SessionStatus.CRASHED in statuses)
    }
}
