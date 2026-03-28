package com.novaterm.core.session.manager

import app.cash.turbine.test
import com.novaterm.core.common.contract.SessionEvent
import com.novaterm.core.common.model.SessionInfo
import com.novaterm.core.common.model.SessionStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the SessionEvent flow contract used by TermuxSessionManager.
 * Uses channels directly to verify the event protocol without needing
 * a real TerminalSession (which requires Android PTY).
 */
class SessionManagerEventTest {

    private val events = Channel<SessionEvent>(Channel.UNLIMITED)
    private val eventsFlow: Flow<SessionEvent> = events.receiveAsFlow()

    @Test
    fun `created event carries session id`() = runTest {
        eventsFlow.test {
            events.send(SessionEvent.Created(sessionId = 42))

            val event = awaitItem()
            assertTrue(event is SessionEvent.Created)
            assertEquals(42, (event as SessionEvent.Created).sessionId)
        }
    }

    @Test
    fun `destroyed event carries exit code`() = runTest {
        eventsFlow.test {
            events.send(SessionEvent.Destroyed(sessionId = 1, exitCode = 0))

            val event = awaitItem()
            assertTrue(event is SessionEvent.Destroyed)
            assertEquals(0, (event as SessionEvent.Destroyed).exitCode)
        }
    }

    @Test
    fun `destroyed event with null exit code means still running`() = runTest {
        eventsFlow.test {
            events.send(SessionEvent.Destroyed(sessionId = 1, exitCode = null))

            val event = awaitItem() as SessionEvent.Destroyed
            assertEquals(null, event.exitCode)
        }
    }

    @Test
    fun `title changed event propagates new title`() = runTest {
        eventsFlow.test {
            events.send(SessionEvent.TitleChanged(sessionId = 1, title = "vim"))

            val event = awaitItem() as SessionEvent.TitleChanged
            assertEquals("vim", event.title)
            assertEquals(1, event.sessionId)
        }
    }

    @Test
    fun `events arrive in order`() = runTest {
        eventsFlow.test {
            events.send(SessionEvent.Created(1))
            events.send(SessionEvent.TitleChanged(1, "bash"))
            events.send(SessionEvent.Destroyed(1, 0))

            assertTrue(awaitItem() is SessionEvent.Created)
            assertTrue(awaitItem() is SessionEvent.TitleChanged)
            assertTrue(awaitItem() is SessionEvent.Destroyed)
        }
    }

    @Test
    fun `UNLIMITED channel never drops events`() = runTest {
        // Send many events before collecting
        repeat(1000) { i ->
            events.send(SessionEvent.Created(i))
        }

        eventsFlow.test {
            repeat(1000) { i ->
                val event = awaitItem() as SessionEvent.Created
                assertEquals(i, event.sessionId)
            }
        }
    }

    @Test
    fun `sessions StateFlow publishes list updates`() = runTest {
        val sessions = MutableStateFlow<List<SessionInfo>>(emptyList())

        sessions.test {
            assertEquals(emptyList<SessionInfo>(), awaitItem())

            val session1 = SessionInfo(
                id = 1, pid = 100, title = "bash",
                cwd = "/home", status = SessionStatus.RUNNING,
            )
            sessions.value = listOf(session1)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("bash", updated[0].title)
            assertEquals(SessionStatus.RUNNING, updated[0].status)
        }
    }

    @Test
    fun `session removal updates list correctly`() = runTest {
        val sessions = MutableStateFlow(listOf(
            SessionInfo(id = 1, pid = 100, title = "bash", cwd = "/home", status = SessionStatus.RUNNING),
            SessionInfo(id = 2, pid = 101, title = "zsh", cwd = "/home", status = SessionStatus.RUNNING),
        ))

        sessions.test {
            val initial = awaitItem()
            assertEquals(2, initial.size)

            // Remove first session
            sessions.value = sessions.value.filter { it.id != 1 }

            val after = awaitItem()
            assertEquals(1, after.size)
            assertEquals("zsh", after[0].title)
        }
    }
}
