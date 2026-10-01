package com.nath.hermesremote.data

import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder

/**
 * The bridge speaks plain HTTP. Two ways to reach it, either acceptable:
 *
 * - the local network (RFC1918 / link-local / unique-local). WireGuard is not involved, so the
 *   device token is the only authenticator.
 * - Tailscale (WireGuard-encrypted): 100.64.0.0/10, fd7a:115c:a1e0::/48, or *.ts.net.
 *
 * Anything else — a public address, a hostname that is neither — is refused at pairing time, so
 * a mistyped URL cannot point the app at some other host on the internet.
 */
object Tailnet {
    /** Host is reachable over the local network. */
    fun isLanHost(host: String): Boolean {
        val h = normalize(host)
        // Tailscale's IPv6 range is itself inside fd00::/8; it is the tailnet, not the LAN.
        if (isTailnetHost(h)) return false
        if (h.contains(':')) return h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80:")
        val parts = h.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 172 -> octets[1] in 16..31
            octets[0] == 192 -> octets[1] == 168
            octets[0] == 169 && octets[1] == 254 -> true
            else -> false
        }
    }

    fun isTailnetHost(host: String): Boolean {
        val h = normalize(host)
        if (h.endsWith(".ts.net")) return true
        if (h.startsWith("fd7a:115c:a1e0:")) return true
        val parts = h.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 100 && octets[1] in 64..127
    }

    /** Loopback is fine for a same-device debug session but never for a phone. */
    fun isAllowedHost(host: String): Boolean = isTailnetHost(host) || isLanHost(host)

    private fun normalize(raw: String) = raw.trim().removePrefix("[").removeSuffix("]").lowercase()

    fun isTailnetAddress(addr: InetAddress): Boolean = when (addr) {
        is Inet4Address -> addr.address.let { (it[0].toInt() and 0xff) == 100 && (it[1].toInt() and 0xc0) == 64 }
        else -> addr.address.size == 16 && addr.address.let {
            (it[0].toInt() and 0xff) == 0xfd && (it[1].toInt() and 0xff) == 0x7a &&
                (it[2].toInt() and 0xff) == 0x11 && (it[3].toInt() and 0xff) == 0x5c &&
                (it[4].toInt() and 0xff) == 0xa1 && (it[5].toInt() and 0xff) == 0xe0
        }
    }
}

object PairingParser {
    /** Parses `hermesremote://pair?v=1&url=…&device=…&token=hrb_…`. */
    fun parseUri(raw: String): Pairing {
        val uri = try { URI(raw.trim()) } catch (e: Exception) { throw IllegalArgumentException("Not a pairing code") }
        require(uri.scheme == "hermesremote" && uri.host == "pair") { "Not a Hermes Remote pairing code" }
        val query = (uri.rawQuery ?: "").split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
        require(query["v"] == "1" || query["v"] == "2") { "Unsupported pairing code version; update the app" }
        val pin = query["pin"]?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) }
        require(query["v"] == "1" || pin != null) { "Pairing code is missing the PC's certificate" }
        val p = validate(query["url"].orEmpty(), query["token"].orEmpty(), query["device"].orEmpty(), pin)
        return p.copy(alternates = alternates(query["alt"]).filter { secure(it, pin) })
    }

    /** Fallback addresses from the code (e.g. Tailscale); anything not LAN/tailnet is dropped. */
    fun alternates(raw: String?): List<String> = raw.orEmpty().split(',')
        .map { it.trim().trimEnd('/') }
        .filter { url -> runCatching { URI(url).host }.getOrNull()?.let(Tailnet::isAllowedHost) == true }
        .distinct()

    /** A LAN address is only usable over HTTPS with a pin; Tailscale is fine over HTTP. */
    fun secure(url: String, pin: String?): Boolean {
        val u = runCatching { URI(url) }.getOrNull() ?: return false
        val host = u.host ?: return false
        return Transport.allowsToken(host.trim('[', ']'), u.scheme == "https", pin != null)
    }

    fun validate(url: String, token: String, device: String = "", pin: String? = null): Pairing {
        val cleanUrl = url.trim().trimEnd('/')
        val parsed = try { URI(cleanUrl) } catch (e: Exception) { throw IllegalArgumentException("Invalid bridge URL") }
        require(parsed.scheme == "http" || parsed.scheme == "https") { "Bridge URL must start with http://" }
        val host = parsed.host ?: throw IllegalArgumentException("Bridge URL has no host")
        require(Tailnet.isAllowedHost(host)) {
            "Bridge must be a local-network address (192.168.x.x, 10.x, 172.16-31.x) or a Tailscale address (100.x.y.z / *.ts.net)"
        }
        val t = token.trim()
        require(t.startsWith("hrb_") && t.length in 20..200) { "Token must start with hrb_" }
        require(secure(cleanUrl, pin)) {
            "A local-network address needs the pairing QR code (it carries the PC's certificate). " +
                "Type a Tailscale address instead, or scan the code."
        }
        return Pairing(cleanUrl, device.trim().ifEmpty { "this device" }, t, pin = pin)
    }
}
