package com.nath.hermesremote

import com.nath.hermesremote.data.ChatItem
import com.nath.hermesremote.data.Endpoint
import com.nath.hermesremote.data.EndpointResolver
import com.nath.hermesremote.data.Transport
import com.nath.hermesremote.data.TransportMode
import com.nath.hermesremote.data.HistoryMapper
import com.nath.hermesremote.data.LanDiscovery
import com.nath.hermesremote.data.LiveReducer
import com.nath.hermesremote.data.PairingParser
import com.nath.hermesremote.data.parseCatalog
import com.nath.hermesremote.data.parseSession
import com.nath.hermesremote.data.SseEvent
import com.nath.hermesremote.data.SseParser
import com.nath.hermesremote.data.Tailnet
import com.nath.hermesremote.data.ToolStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun ev(id: Long, name: String, data: String = "{}") = SseEvent(id, name, JSONObject(data))

    @Test fun sseParserHandlesFramesAndKeepalives() {
        val p = SseParser()
        val lines = listOf(": keepalive", "", "id: 3", "event: message.delta", "data: {\"delta\":\"hi\"}", "",
            "id: 4", "event: run.completed", "data: {\"output\":", "data: \"x\"}", "")
        val out = lines.mapNotNull { p.feed(it) }
        assertEquals(listOf(3L, 4L), out.map { it.id })
        assertEquals("hi", out[0].data.getString("delta"))
        assertEquals("x", out[1].data.getString("output"))
    }

    @Test fun reducerStreamsToolsAndCompletion() {
        var items: List<ChatItem> = listOf(ChatItem.User("u", "run uname"))
        items = LiveReducer.apply(items, ev(1, "tool.started", """{"tool":"terminal","preview":"uname -r"}"""))
        assertEquals(ToolStatus.RUNNING, (items[1] as ChatItem.Tool).status)
        items = LiveReducer.apply(items, ev(2, "tool.completed", """{"tool":"terminal","duration":0.4,"error":false,"preview":"ok"}"""))
        assertEquals(ToolStatus.OK, (items[1] as ChatItem.Tool).status)
        items = LiveReducer.apply(items, ev(3, "message.delta", """{"delta":"\n\n7.1"}"""))
        items = LiveReducer.apply(items, ev(4, "message.delta", """{"delta":".8"}"""))
        assertEquals("7.1.8", (items.last() as ChatItem.Assistant).text)
        items = LiveReducer.apply(items, ev(5, "run.completed", """{"output":"7.1.8"}"""))
        val last = items.last() as ChatItem.Assistant
        assertFalse(last.streaming)
        assertEquals(3, items.size) // no duplicate final bubble
    }

    @Test fun reducerCancelledMarksRunningToolsFailed() {
        var items: List<ChatItem> = emptyList()
        items = LiveReducer.apply(items, ev(1, "tool.started", """{"tool":"terminal","preview":"sleep 60"}"""))
        items = LiveReducer.apply(items, ev(2, "run.cancelled", "{}"))
        assertEquals(ToolStatus.FAILED, (items[0] as ChatItem.Tool).status)
        assertTrue(items.last() is ChatItem.Notice)
    }

    @Test fun reducerApprovalLifecycle() {
        var items: List<ChatItem> = emptyList()
        items = LiveReducer.apply(items, ev(1, "approval.request", """{"command":"rm -rf x","description":"recursive delete","choices":["once","session","deny"]}"""))
        val a = items[0] as ChatItem.Approval
        assertEquals(listOf("once", "session", "deny"), a.request.choices)
        items = LiveReducer.apply(items, ev(2, "approval.responded", """{"choice":"deny"}"""))
        assertEquals("deny", (items[0] as ChatItem.Approval).decided)
    }

    @Test fun historyAttachesToolResultsByCallId() {
        val msgs = JSONArray("""[
          {"id":"1","role":"user","content":"go"},
          {"id":"2","role":"assistant","content":"","tool_calls":[{"id":"c1","function":{"name":"terminal","arguments":"{\"command\":\"uname -r\"}"}}]},
          {"id":"3","role":"tool","content":"{\"output\":\"x\",\"exit_code\":1}","tool_call_id":"c1","tool_name":"terminal"},
          {"id":"4","role":"assistant","content":"done"}]""")
        val items = HistoryMapper.map(msgs)
        assertEquals(3, items.size)
        val t = items[1] as ChatItem.Tool
        assertEquals("uname -r", t.args)
        assertEquals(ToolStatus.FAILED, t.status)
        assertEquals("go", HistoryMapper.lastUserText(msgs))
        assertEquals(0, HistoryMapper.withoutLastTurn(msgs).length())
    }

    @Test fun catalogParsesAndDropsUnavailableProviders() {
        val raw = JSONObject("""
          {"current": {"model": "claude-opus-5-5", "provider": "anthropic"},
           "providers": [
             {"slug": "anthropic", "name": "Anthropic", "current": true,
              "models": ["claude-opus-5-5", "claude-sonnet-5"], "featured": ["claude-opus-5-5"]},
             {"slug": "moa", "name": "Mixture of Agents", "current": false,
              "models": ["default"], "featured": []}]}""")
        val cat = parseCatalog(raw)
        assertEquals("claude-opus-5-5", cat.currentModel)
        assertEquals(3, cat.options.size)
        // featured first inside a provider
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5", "default"), cat.options.map { it.id })
        assertEquals("Mixture of Agents", cat.options.last().providerName)
        assertEquals("claude-opus-5-5", cat.selected?.id)
    }

    @Test fun sessionSummaryCarriesPinnedFlag() {
        val s = parseSession(JSONObject("""{"id":"a","title":"t","message_count":3,"pinned":true,"model":"m"}"""))
        assertTrue(s.pinned)
        assertEquals("m", s.model)
        assertFalse(parseSession(JSONObject("""{"id":"b"}""")).pinned)
    }

    @Test fun tailnetGuard() {
        assertTrue(Tailnet.isAllowedHost("100.64.0.10"))
        assertTrue(Tailnet.isAllowedHost("mypc.tail0000.ts.net"))
        assertTrue(Tailnet.isAllowedHost("[fd7a:115c:a1e0::10]"))
        // RFC1918 is a valid bridge host too; the LAN is an accepted transport.
        assertTrue(Tailnet.isAllowedHost("192.168.1.10"))
        assertFalse(Tailnet.isAllowedHost("100.128.0.1"))
        assertFalse(Tailnet.isAllowedHost("evil.com"))
        assertFalse(Tailnet.isAllowedHost("ts.net.evil.com"))
    }

    @Test fun pairingUri() {
        val p = PairingParser.parseUri("hermesremote://pair?v=1&url=http%3A%2F%2F100.64.0.10%3A8650&device=phone&token=hrb_abcdefghijklmnopqrstuvwxyz")
        assertEquals("http://100.64.0.10:8650", p.url)
        assertEquals("phone", p.device)
        // A LAN address only with HTTPS + the certificate pin (v2); never plain HTTP.
        val pin = "xmgVkYVEEPnn5I36kWsDCLEGBwMMT3_Lds2BLuH9gTg"
        val lan = PairingParser.parseUri("hermesremote://pair?v=2&url=https%3A%2F%2F192.168.1.5%3A8650&device=x&token=hrb_abcdefghijklmnopqrstuvwxyz&pin=$pin")
        assertEquals("https://192.168.1.5:8650", lan.url)
        assertEquals(pin, lan.pin)
        assertThrows(IllegalArgumentException::class.java) {  // plain-HTTP LAN: token would cross Wi-Fi in clear
            PairingParser.parseUri("hermesremote://pair?v=1&url=http%3A%2F%2F192.168.1.5%3A8650&device=x&token=hrb_abcdefghijklmnopqrstuvwxyz")
        }
        assertThrows(IllegalArgumentException::class.java) {  // v2 without a pin
            PairingParser.parseUri("hermesremote://pair?v=2&url=https%3A%2F%2F192.168.1.5%3A8650&device=x&token=hrb_abcdefghijklmnopqrstuvwxyz")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingParser.parseUri("hermesremote://pair?v=1&url=http%3A%2F%2F203.0.113.9%3A8650&device=x&token=hrb_abcdefghijklmnopqrstuvwxyz")
        }
        assertThrows(IllegalArgumentException::class.java) { PairingParser.validate("http://100.64.0.10:8650", "nope") }
        assertNull(runCatching { PairingParser.parseUri("https://example.com") }.getOrNull())
    }
}

/** In-memory SharedPreferences: exercises DraftStore's real logic without Robolectric. */
private class FakePrefs : android.content.SharedPreferences {
    val map = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST") override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
        map[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String?) = map.containsKey(key)
    override fun edit() = object : android.content.SharedPreferences.Editor {
        private val pending = mutableMapOf<String?, Any?>()
        private val removed = mutableSetOf<String?>()
        override fun putString(k: String?, v: String?) = also { pending[k] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = also { pending[k] = v }
        override fun putInt(k: String?, v: Int) = also { pending[k] = v }
        override fun putLong(k: String?, v: Long) = also { pending[k] = v }
        override fun putFloat(k: String?, v: Float) = also { pending[k] = v }
        override fun putBoolean(k: String?, v: Boolean) = also { pending[k] = v }
        override fun remove(k: String?) = also { removed.add(k) }
        override fun clear() = also { map.keys.toList().forEach(removed::add) }
        override fun commit(): Boolean { flush(); return true }
        override fun apply() = flush()
        private fun flush() {
            pending.forEach { (k, v) -> if (k != null) map[k] = v }
            removed.forEach { k -> if (k != null) map.remove(k) }
            pending.clear(); removed.clear()
        }
    }
    override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

class DraftStoreTest {
    private val store = com.nath.hermesremote.data.DraftStore(FakePrefs())

    @Test fun draftSurvivesReopeningTheSession() {
        store.put("s1", "half written")
        assertEquals("half written", store.get("s1"))
        assertEquals("", store.get("s2"))
    }

    @Test fun draftsAreSeparatePerSessionAndForNewChats() {
        store.put(null, "unsent new chat")
        store.put("s1", "unsent s1")
        assertEquals("unsent new chat", store.get(null))
        assertEquals("unsent s1", store.get("s1"))
        store.clear("s1")
        assertEquals("", store.get("s1"))
        assertEquals("unsent new chat", store.get(null))
    }

    @Test fun blankDraftIsRemovedNotStored() {
        store.put("s1", "text")
        store.put("s1", "   ")
        assertEquals("", store.get("s1"))
    }

    @Test fun pruneKeepsLiveSessionsAndCurrentDrafts() {
        store.put("s1", "a"); store.put("gone", "b"); store.put(null, "c")
        store.prune(keep = setOf("s1"), keepText = mapOf("__new__" to "c"))
        assertEquals("a", store.get("s1"))
        assertEquals("", store.get("gone"))
        assertEquals("c", store.get(null))
    }
}

class EndpointResolverTest {
    private val addrs = mapOf("lan" to listOf("192.168.1.10"), "tailnet" to listOf("100.64.0.10", "fd7a:115c:a1e0::1"))

    @Test fun lanAndTailnetHostsAreBothAccepted() {
        assertTrue(Tailnet.isAllowedHost("192.168.1.10"))
        assertTrue(Tailnet.isAllowedHost("10.0.0.5"))
        assertTrue(Tailnet.isAllowedHost("172.16.4.4"))
        assertTrue(Tailnet.isAllowedHost("100.64.0.10"))
        assertTrue(Tailnet.isAllowedHost("mypc.tail0000.ts.net"))
        // 172.15/172.32 are outside RFC1918; a public address is never a bridge.
        assertFalse(Tailnet.isAllowedHost("172.32.0.1"))
        assertFalse(Tailnet.isAllowedHost("203.0.113.9"))
        assertFalse(Tailnet.isAllowedHost("example.com"))
        assertFalse(Tailnet.isAllowedHost("8.8.8.8"))
    }

    @Test fun lanAndTailnetAreNotConfused() {
        assertEquals(Transport.LAN, EndpointResolver.transportOf("192.168.1.10"))
        assertEquals(Transport.TAILNET, EndpointResolver.transportOf("100.64.0.10"))
        assertTrue(Tailnet.isTailnetHost("100.64.0.10"))
        assertFalse(Tailnet.isLanHost("100.64.0.10"))
    }

    @Test fun autoPrefersLanThenFallsBackToTailscale() {
        // Even when the saved address is the tailnet one (e.g. after a manual switch), AUTO
        // must try the LAN first; that is what "Tailscale is optional" means.
        val order = EndpointResolver.resolve("http://100.64.0.10:8650", addrs, TransportMode.AUTO).map { it.host }
        assertEquals(listOf("192.168.1.10", "100.64.0.10", "fd7a:115c:a1e0::1"), order)
    }

    @Test fun manualModeRestrictsToThatNetwork() {
        val ts = EndpointResolver.resolve("http://192.168.1.10:8650", addrs, TransportMode.TAILNET).map { it.host }
        assertEquals(listOf("100.64.0.10", "fd7a:115c:a1e0::1"), ts)
        val lan = EndpointResolver.resolve("http://100.64.0.10:8650", addrs, TransportMode.LAN).map { it.host }
        assertEquals(listOf("192.168.1.10"), lan)
    }

    @Test fun pairedLanAddressIsTriedFirst() {
        val order = EndpointResolver.resolve("http://192.168.1.10:8650", addrs, TransportMode.AUTO).map { it.host }
        assertEquals("192.168.1.10", order.first())
    }

    @Test fun forcedTransportOnlyOffersThatNetwork() {
        val lan = EndpointResolver.resolve("http://100.64.0.10:8650", addrs, force = Transport.LAN)
        assertEquals(listOf("192.168.1.10"), lan.map { it.host })
        val ts = EndpointResolver.resolve("http://192.168.1.10:8650", addrs, force = Transport.TAILNET)
        assertEquals(listOf("100.64.0.10", "fd7a:115c:a1e0::1"), ts.map { it.host })
    }

    @Test fun worksWithNoAdvertisedAddresses() {
        // An older bridge has no `addresses`; the paired address must still be used.
        val only = EndpointResolver.resolve("http://100.64.0.10:8650", null)
        assertEquals(1, only.size)
        assertEquals("http://100.64.0.10:8650", only.first().url)
    }

    @Test fun noDuplicateHostsAndPortIsCarried() {
        val dupes = mapOf("lan" to listOf("192.168.1.10", "192.168.1.10"), "tailnet" to listOf("192.168.1.10"))
        val eps = EndpointResolver.resolve("http://192.168.1.10:9999", dupes)
        assertEquals(eps.map { it.host }.distinct().size, eps.size)
        assertTrue(eps.all { it.port == 9999 })
    }

    @Test fun ipv6HostIsBracketedInUrl() {
        val ep = EndpointResolver.resolve("http://[fd7a:115c:a1e0::1]:8650", mapOf("tailnet" to listOf("fd7a:115c:a1e0::1")))
        assertEquals("http://[fd7a:115c:a1e0::1]:8650", ep.first().url)
    }
}

class NotifierTextTest {
    @Test fun markdownIsFlattenedForTheShade() {
        val md = "## Done\n\nI fixed **two** bugs in `app.py`, see [the PR](https://x.y/1).\n\n```kotlin\nval a = 1\n```\n\n\n\nBye"
        val out = com.nath.hermesremote.data.Notifier.plain(md)
        assertEquals("Done\n\nI fixed two bugs in app.py, see the PR.\n\n[code]\n\nBye", out)
    }
}

class PairingFallbackTest {
    private val pin = "xmgVkYVEEPnn5I36kWsDCLEGBwMMT3_Lds2BLuH9gTg"

    @Test fun alternatesAreReadAndFiltered() {
        val p = PairingParser.parseUri("hermesremote://pair?v=2&url=https%3A%2F%2F192.168.1.10%3A8650&device=phone" +
            "&token=hrb_abcdefghijklmnopqrstuvwxyz&pin=$pin" +
            "&alt=http%3A%2F%2F100.64.0.10%3A8650%2Chttp%3A%2F%2F8.8.8.8%3A8650%2Chttp%3A%2F%2F10.0.0.9%3A8650")
        assertEquals("https://192.168.1.10:8650", p.url)
        // Public address dropped; plain-HTTP LAN dropped; the tailnet fallback is kept.
        assertEquals(listOf("http://100.64.0.10:8650"), p.alternates)
    }

    @Test fun oldTailscaleCodesStillWork() {
        val p = PairingParser.parseUri("hermesremote://pair?v=1&url=http%3A%2F%2F100.64.0.10%3A8650&device=x&token=hrb_abcdefghijklmnopqrstuvwxyz")
        assertTrue(p.alternates.isEmpty())
        assertNull(p.pin)
    }

    @Test fun tailscaleIpv6IsNotLan() {
        assertFalse(Tailnet.isLanHost("fd7a:115c:a1e0::10"))
        assertTrue(Tailnet.isTailnetHost("fd7a:115c:a1e0::10"))
        assertTrue(Tailnet.isLanHost("fd12:3456::1"))
    }

    @Test fun tokenOnlyGoesOverAVerifiedLink() {
        assertTrue(Transport.allowsToken("100.64.0.10", https = false, pinned = false))
        assertTrue(Transport.allowsToken("192.168.1.10", https = true, pinned = true))
        assertFalse(Transport.allowsToken("192.168.1.10", https = false, pinned = true))
        assertFalse(Transport.allowsToken("192.168.1.10", https = true, pinned = false))
        assertFalse(Transport.allowsToken("8.8.8.8", https = true, pinned = true))
    }

    @Test fun lanEndpointsAreHttps() {
        assertEquals("https://192.168.1.10:8650", Endpoint(Transport.LAN, "192.168.1.10", 8650).url)
        assertEquals("http://100.64.0.10:8650", Endpoint(Transport.TAILNET, "100.64.0.10", 8650).url)
    }
}

class LanDiscoveryHostsTest {
    @Test fun advertisedAddressesWinOverADockerResolve() {
        // Avahi announces on docker0 too; the resolver may hand back 172.17.0.1.
        assertEquals(listOf("192.168.1.20"), LanDiscovery.hostsFrom("192.168.1.20", "172.17.0.1"))
    }

    @Test fun olderBridgeFallsBackToTheResolvedHost() {
        assertEquals(listOf("192.168.1.20"), LanDiscovery.hostsFrom(null, "192.168.1.20"))
    }

    @Test fun garbageAndPublicAddressesAreIgnored() {
        assertEquals(emptyList<String>(), LanDiscovery.hostsFrom("8.8.8.8,evil.example", null))
    }
}
