package io.github.nideta231.hermesremote

import io.github.nideta231.hermesremote.data.BridgeClient
import io.github.nideta231.hermesremote.data.BridgeException
import io.github.nideta231.hermesremote.data.ChatItem
import io.github.nideta231.hermesremote.data.HistoryMapper
import io.github.nideta231.hermesremote.data.LiveReducer
import io.github.nideta231.hermesremote.data.Pairing
import io.github.nideta231.hermesremote.data.SseEvent
import io.github.nideta231.hermesremote.data.ToolStatus
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Runs the app's real network + reducer code against a live bridge.
 * Enabled only when HERMES_REMOTE_E2E points to a creds JSON written by `pair --token-file`.
 */
class LiveBridgeTest {
    private val creds = System.getenv("HERMES_REMOTE_E2E")?.let { JSONObject(File(it).readText()) }

    @Test fun fullAgentFlow() = runBlocking<Unit> {
        assumeTrue("set HERMES_REMOTE_E2E to run", creds != null)
        val c = BridgeClient(Pairing(creds!!.getString("url"), creds.getString("device"), creds.getString("token")))

        assertEquals(creds.getString("device"), c.me().getJSONObject("device").getString("name"))
        val status = c.status().associateBy { it.key }
        assertTrue("hermes ok", status["hermes"]!!.ok)
        assertTrue("desktop ok", status["desktop"]!!.ok)
        assertEquals(3389, c.desktop().port)

        val s = c.createSession("android-e2e")
        val reqId = UUID.randomUUID().toString().replace("-", "")
        val run = c.startRun(s.id, "Use the terminal tool to run: sleep 4; echo kotlin-ok . Then reply with only its output.", reqId)
        assertEquals(run.runId, c.startRun(s.id, "dup", reqId).runId) // dedup

        // Read 1 event, "lose the connection", then resume from that cursor like AppViewModel does.
        var items: List<ChatItem> = listOf(ChatItem.User("u", "x"))
        val first = c.events(run.runId, 0).take(1).toList()
        val seen = mutableListOf<SseEvent>()
        seen += first
        Thread.sleep(6000)
        seen += c.events(run.runId, first.last().id).toList()
        assertEquals((1L..seen.size).toList(), seen.map { it.id })
        seen.forEach { items = LiveReducer.apply(items, it) }
        assertEquals("run.completed", seen.last().name)
        val tool = items.filterIsInstance<ChatItem.Tool>().single()
        assertEquals(ToolStatus.OK, tool.status)
        assertTrue((items.last() as ChatItem.Assistant).text.contains("kotlin-ok"))

        val (_, active) = c.session(s.id)
        assertEquals(null, active)
        val history = HistoryMapper.map(c.messages(s.id))
        assertTrue(history.any { it is ChatItem.Tool && it.name == "terminal" })
        assertTrue((history.last() as ChatItem.Assistant).text.contains("kotlin-ok"))

        c.deleteSession(s.id)
        val gone = runCatching { c.session(s.id) }.exceptionOrNull()
        assertTrue(gone is BridgeException && gone.httpCode == 404)
    }

    @Test fun modelCatalogAndPinnedFlag() = runBlocking<Unit> {
        assumeTrue("set HERMES_REMOTE_E2E to run", creds != null)
        val c = BridgeClient(Pairing(creds!!.getString("url"), creds.getString("device"), creds.getString("token")))

        val cat = c.models()
        assertTrue(cat.options.isNotEmpty())
        assertTrue(cat.options.all { it.provider.isNotBlank() && it.id.isNotBlank() })
        // The picker must not offer a model the account cannot run.
        assertTrue(cat.options.none { it.id.contains("unavailable") })

        val s = c.createSession("pin-probe")
        try {
            c.setPinned(s.id, true)
            assertTrue(c.session(s.id).first.pinned)
            val listed = c.sessions(limit = 100).first.first { it.id == s.id }
            assertTrue("pinned row is listed as pinned", listed.pinned)
            c.setPinned(s.id, false)
            assertFalse(c.session(s.id).first.pinned)
        } finally {
            c.deleteSession(s.id)
        }
    }

    @Test fun followsSessionDrivenElsewhere() = runBlocking<Unit> {
        assumeTrue("set HERMES_REMOTE_E2E to run", creds != null)
        val c = BridgeClient(Pairing(creds!!.getString("url"), creds.getString("device"), creds.getString("token")))

        val s = c.createSession("follow-probe")
        val sid = s.id
        val first = c.sync(sid, 0)
        assertEquals(0, first.cursor)
        assertTrue(first.messages.isEmpty())

        // Simulate the other surface: a run started outside the bridge appends messages.
        val run = c.startRun(sid, "Reply with only: followed", UUID.randomUUID().toString().replace("-", ""))
        c.events(run.runId, 0).toList()
        val (_, _) = c.session(sid)

        val tail = c.sync(sid, first.cursor)
        assertTrue("new messages were tailed", tail.messages.isNotEmpty())
        assertTrue(tail.cursor > first.cursor)
        // A second poll at the new cursor must add nothing (no duplicates).
        val again = c.sync(sid, tail.cursor)
        assertTrue(again.messages.isEmpty())
        assertEquals(tail.cursor, again.cursor)
        c.deleteSession(sid)
    }
}
