package io.github.nideta231.hermesremote

import io.github.nideta231.hermesremote.data.BridgeClient
import io.github.nideta231.hermesremote.data.ChatItem
import io.github.nideta231.hermesremote.data.GatewayState
import io.github.nideta231.hermesremote.data.HistoryMapper
import io.github.nideta231.hermesremote.data.Incoming
import io.github.nideta231.hermesremote.data.LiveReducer
import io.github.nideta231.hermesremote.data.Pairing
import io.github.nideta231.hermesremote.data.ToolStatus
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the app's real socket + reducer code against a live bridge and the desktop's hermes serve.
 * Enabled only when HERMES_REMOTE_E2E points to a creds JSON written by `pair --token-file`.
 */
class LiveBridgeTest {
    private val creds = System.getenv("HERMES_REMOTE_E2E")?.let { JSONObject(File(it).readText()) }

    // A scratch bridge on loopback is not a tailnet host; the guard is the only thing relaxed here.
    private fun client() = BridgeClient(Pairing(creds!!.getString("url"), creds.getString("device"), creds.getString("token")),
        hostGuard = { host, https -> host == "127.0.0.1" || io.github.nideta231.hermesremote.data.Transport.allowsToken(host, https, false) })

    @Test fun fullAgentFlowOverTheSharedSocket() = runBlocking<Unit> {
        assumeTrue("set HERMES_REMOTE_E2E to run", creds != null)
        val c = client()
        assertEquals(creds!!.getString("device"), c.me().getJSONObject("device").getString("name"))
        assertTrue("hermes ok", c.status().first { it.key == "hermes" }.ok)

        val g = c.gateway()
        g.start()
        try {
            withTimeout(20_000) { g.state.first { it == GatewayState.OPEN } }
            val created = g.call("session.create", JSONObject().put("source", "desktop").put("cols", 96), 60_000)
            val rid = created.getString("session_id")
            val stored = created.optString("stored_session_id", rid)
            try {
                val events = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    g.incoming.filterIsInstance<Incoming.Event>()
                        .transformWhile { emit(it); !(it.sessionId == rid && it.type == "message.complete") }
                        .toList()
                }
                g.call("prompt.submit", JSONObject().put("session_id", rid)
                    .put("text", "Use the terminal tool to run: echo kotlin-ok . Then reply with only its output."))
                val all = withTimeout(240_000) { events.await() }
                val seen = all.filter { it.sessionId == rid }
                val trace = all.joinToString("\n") { "${it.sessionId == rid} ${it.type} ${it.payload.toString().take(200)}" }
                var items: List<ChatItem> = listOf(ChatItem.User("u", "x"))
                seen.forEach { items = LiveReducer.apply(items, it.type, it.payload, "${it.seq}") }
                // Short replies may arrive whole in message.complete with no deltas; either way it must show.
                assertTrue("reply shown; got:\n$trace", items.filterIsInstance<ChatItem.Assistant>().any { it.text.contains("kotlin-ok") })
                assertTrue("no spinner text in the transcript", items.none { it is ChatItem.Thinking && it.text.contains("...") && it.text.length < 40 && "(" in it.text })
                assertTrue("tool ran", items.any { it is ChatItem.Tool && it.name == "terminal" })
                assertTrue(items.filterIsInstance<ChatItem.Tool>().none { it.status == ToolStatus.RUNNING })

                // The desktop sees the same session: resume returns the transcript Hermes stored.
                val resumed = g.call("session.resume", JSONObject().put("session_id", stored).put("cols", 96), 60_000)
                val history = HistoryMapper.map(resumed.optJSONArray("messages"))
                assertTrue(history.any { it is ChatItem.Tool && it.name == "terminal" })
                assertTrue(history.filterIsInstance<ChatItem.Assistant>().last().text.contains("kotlin-ok"))
                assertTrue("listed like the desktop sidebar", c.sessions(100, 0).first.any { it.id == stored })
            } finally {
                runCatching { g.call("session.close", JSONObject().put("session_id", rid)) }
                g.call("session.delete", JSONObject().put("session_id", stored))
            }
        } finally { g.close() }
    }

    @Test fun modelCatalogAndPinnedFlag() = runBlocking<Unit> {
        assumeTrue("set HERMES_REMOTE_E2E to run", creds != null)
        val c = client()
        val g = c.gateway()
        g.start()
        try {
            withTimeout(20_000) { g.state.first { it == GatewayState.OPEN } }
            val cat = io.github.nideta231.hermesremote.data.parseCatalog(g.call("model.options", JSONObject(), 30_000))
            assertTrue(cat.options.isNotEmpty())
            assertTrue(cat.options.all { it.provider.isNotBlank() && it.id.isNotBlank() })

            val created = g.call("session.create", JSONObject().put("source", "desktop"), 60_000)
            val rid = created.getString("session_id")
            val stored = created.optString("stored_session_id", rid)
            try {
                // A session is listed once it has a message; make one cheaply via /title.
                g.call("session.title", JSONObject().put("session_id", rid).put("title", "pin-probe"))
                c.setPinned(stored, true)
                assertTrue(c.sessions(100, 0).first.first { it.id == stored }.pinned)
                c.setPinned(stored, false)
            } finally {
                runCatching { g.call("session.close", JSONObject().put("session_id", rid)) }
                g.call("session.delete", JSONObject().put("session_id", stored))
            }
        } finally { g.close() }
    }
}
