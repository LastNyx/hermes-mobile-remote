package io.github.nideta231.hermesremote

import io.github.nideta231.hermesremote.data.BridgeClient
import io.github.nideta231.hermesremote.data.CommandReply
import io.github.nideta231.hermesremote.data.Pairing
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** What the app actually puts on the wire for runs and slash commands. */
class BridgeRequestsTest {
    private val server = MockWebServer()
    private lateinit var client: BridgeClient

    @Before fun setUp() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        client = BridgeClient(Pairing("http://127.0.0.1:${server.port}", "t", "hrb_abcdefghijklmnopqrstuvwxyz"),
            hostGuard = { _, _ -> true })
    }

    @After fun tearDown() = server.shutdown()

    private val run = """{"run_id":"r1","session_id":"s1","status":"started"}"""

    @Test fun reasoningEffortIsSentOnlyWhenChosen() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody(run))
        server.enqueue(MockResponse().setResponseCode(202).setBody(run))
        client.startRun("s1", "hi", "req000001", reasoningEffort = "high")
        client.startRun("s1", "hi", "req000002")
        val first = JSONObject(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8())
        val second = JSONObject(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8())
        assertEquals("high", first.getString("reasoning_effort"))
        assertFalse(second.has("reasoning_effort"))
    }

    @Test fun slashCommandPostsToTheSessionAndParsesTheReply() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"type":"send","message":"[expanded]","display":"/plan x"}"""))
        val reply = client.runCommand("s 1", "/plan x")
        val req = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/sessions/s%201/command", req.path)
        assertEquals("/plan x", JSONObject(req.body.readUtf8()).getString("command"))
        assertEquals(CommandReply.Send("[expanded]", "/plan x"), reply)
    }
}
