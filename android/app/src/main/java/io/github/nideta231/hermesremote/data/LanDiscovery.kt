package io.github.nideta231.hermesremote.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * Finds the bridge on the local network over mDNS (`_hermesremote._tcp`), so a PC that got a new
 * IP address (other router, DHCP) is still found without re-pairing.
 *
 * The TXT record carries a short prefix of the certificate pin purely to skip other people's
 * bridges; it proves nothing. The full pin is still checked during the TLS handshake.
 */
class LanDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** IPv4 addresses advertising our bridge, found within [timeoutMs]. */
    suspend fun find(pin: String, timeoutMs: Long = 4000): List<String> {
        val found = mutableListOf<NsdServiceInfo>()
        val started = CompletableDeferred<Boolean>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { started.complete(true) }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { started.complete(false) }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) { synchronized(found) { found.add(info) } }
        }
        runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { return emptyList() }
        try {
            if (withTimeoutOrNull(1500) { started.await() } != true) return emptyList()
            kotlinx.coroutines.delay(timeoutMs)
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        val hosts = mutableListOf<String>()
        for (info in synchronized(found) { found.toList() }) {
            val resolved = withTimeoutOrNull(3000) { resolve(info) } ?: continue
            val txtPin = resolved.attributes["pin"]?.toString(Charsets.UTF_8).orEmpty()
            if (txtPin.isEmpty() || !pin.startsWith(txtPin)) continue
            val advertised = resolved.attributes["ip"]?.toString(Charsets.UTF_8)
            @Suppress("DEPRECATION")
            val resolvedHost = (resolved.host as? Inet4Address)?.hostAddress
            for (h in hostsFrom(advertised, resolvedHost)) if (h !in hosts) hosts.add(h)
        }
        return hosts
    }

    companion object {
        private const val TYPE = "_hermesremote._tcp"

        /**
         * The PC announces on every interface (Docker bridges too), so the resolved host can be
         * an address the phone can't reach. Prefer the `ip=` list of addresses the bridge
         * actually serves; fall back to the resolved host for older bridges.
         */
        fun hostsFrom(advertised: String?, resolvedHost: String?): List<String> {
            val listed = advertised.orEmpty().split(',').map { it.trim() }
                .filter { it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) && Tailnet.isLanHost(it) }
            if (listed.isNotEmpty()) return listed
            return listOfNotNull(resolvedHost?.takeIf(Tailnet::isLanHost))
        }
    }

    @Suppress("DEPRECATION") // resolveService is the API available down to minSdk 26
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(serviceInfo)
                }
            })
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }
}
