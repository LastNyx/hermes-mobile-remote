package io.github.nideta231.hermesremote

import io.github.nideta231.hermesremote.data.BridgeClient
import io.github.nideta231.hermesremote.data.GatewayState
import io.github.nideta231.hermesremote.data.Incoming
import io.github.nideta231.hermesremote.data.Pairing
import io.github.nideta231.hermesremote.data.RpcException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** What the app actually puts on the wire: the bearer token, JSON-RPC calls, answers to Hermes' questions. */
class BridgeRequestsTest {
    private val server = MockWebServer()
    private lateinit var client: BridgeClient
    private val fromPhone = LinkedBlockingQueue<JSONObject>()
    @Volatile private var hermes: WebSocket? = null

    /** A tiny stand-in for hermes serve behind the bridge: answers calls, can push frames. */
    private val fakeHermes = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { hermes = webSocket }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val f = JSONObject(text)
            fromPhone += f
            if (!f.has("method")) return // a reply to one of our requests
            val reply = JSONObject().put("jsonrpc", "2.0").put("id", f.get("id"))
            when (f.getString("method")) {
                "session.steer" -> reply.put("error", JSONObject().put("code", 4009).put("message", "busy"))
                else -> reply.put("result", JSONObject().put("ok", true).put("echo", f.optJSONObject("params")))
            }
            webSocket.send(reply.toString())
        }
    }

    @Before fun setUp() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        client = BridgeClient(Pairing("http://127.0.0.1:${server.port}", "t", "hrb_abcdefghijklmnopqrstuvwxyz"),
            hostGuard = { _, _ -> true })
    }

    @After fun tearDown() {
        runCatching { hermes?.close(1000, null) }
        server.shutdown()
    }

    private fun next(): JSONObject = fromPhone.poll(5, TimeUnit.SECONDS) ?: error("nothing from the phone")

    @Test fun socketAuthenticatesAnnouncesItselfAndRoundTrips() = runBlocking<Unit> {
        server.enqueue(MockResponse().withWebSocketUpgrade(fakeHermes))
        val g = client.gateway()
        g.start()
        try {
            withTimeout(5_000) { g.state.first { it == GatewayState.OPEN } }
            val upgrade = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1/ws", upgrade.path)
            assertEquals("Bearer hrb_abcdefghijklmnopqrstuvwxyz", upgrade.getHeader("Authorization"))
            // First frame: the phone says it can answer approvals, or Hermes would fail them fast.
            val caps = next()
            assertEquals("client.capabilities", caps.getString("method"))
            assertTrue(caps.getJSONObject("params").getBoolean("server_requests"))

            val res = g.call("prompt.submit", JSONObject().put("session_id", "rt1").put("text", "hi"))
            assertEquals("hi", res.getJSONObject("echo").getString("text"))
            assertEquals("prompt.submit", next().getString("method"))

            val err = runCatching { g.call("session.steer", JSONObject()) }.exceptionOrNull()
            assertTrue(err is RpcException && err.code == 4009)
            next()
        } finally { g.close() }
    }

    @Test fun eventsAndQuestionsArriveAndAnswersGoBackById() = runBlocking<Unit> {
        server.enqueue(MockResponse().withWebSocketUpgrade(fakeHermes))
        val g = client.gateway()
        g.start()
        try {
            withTimeout(5_000) { g.state.first { it == GatewayState.OPEN } }
            next() // capabilities
            // incoming has no replay (like the app, which subscribes before connecting): listen first.
            val evWait = async(start = CoroutineStart.UNDISPATCHED) { g.incoming.filterIsInstance<Incoming.Event>().first() }
            hermes!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"rt1","seq":7,"payload":{"text":"Hel"}}}""")
            val ev = withTimeout(5_000) { evWait.await() }
            assertEquals("message.delta", ev.type)
            assertEquals("rt1", ev.sessionId)
            assertEquals(7L, ev.seq)
            assertEquals("Hel", ev.payload.getString("text"))

            val askWait = async(start = CoroutineStart.UNDISPATCHED) { g.incoming.filterIsInstance<Incoming.Request>().first() }
            hermes!!.send("""{"jsonrpc":"2.0","id":"srq-9","method":"approval","params":{"session_id":"rt1","command":"rm x"}}""")
            val ask = withTimeout(5_000) { askWait.await() }
            assertEquals("srq-9", ask.id)
            assertTrue(g.reply(ask.id, JSONObject().put("choice", "deny")))
            val answer = next()
            assertEquals("srq-9", answer.getString("id"))
            assertEquals("deny", answer.getJSONObject("result").getString("choice"))
        } finally { g.close() }
    }

    @Test fun revokedTokenStopsReconnecting() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(401))
        val g = client.gateway()
        g.start()
        try {
            withTimeout(5_000) { g.state.first { it == GatewayState.UNAUTHORIZED } }
        } finally { g.close() }
    }
}
