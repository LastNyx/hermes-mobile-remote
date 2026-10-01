package com.nath.hermesremote

import com.nath.hermesremote.data.BridgeClient
import com.nath.hermesremote.data.CertPin
import com.nath.hermesremote.data.Pairing
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Real TLS against a local server with a self-signed certificate, like the bridge's: the right
 * pin connects; a wrong pin (an impostor on the PC's old IP) fails before any request is sent,
 * so the device token never leaves the phone.
 */
class PinnedTlsTest {
    private lateinit var server: MockWebServer
    private lateinit var cert: X509Certificate
    private val dir = File(System.getProperty("java.io.tmpdir"), "pin-test-${System.nanoTime()}").apply { mkdirs() }

    @Before fun setUp() {
        val ks = File(dir, "ks.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val proc = ProcessBuilder(keytool, "-genkeypair", "-alias", "b", "-keyalg", "EC", "-groupname", "secp256r1",
            "-dname", "CN=hermes-remote-bridge", "-validity", "2", "-storetype", "PKCS12",
            "-keystore", ks.path, "-storepass", "testpass", "-keypass", "testpass")
            .redirectErrorStream(true).start()
        proc.waitFor(60, TimeUnit.SECONDS)
        val store = KeyStore.getInstance("PKCS12").apply { ks.inputStream().use { load(it, "testpass".toCharArray()) } }
        cert = store.getCertificate("b") as X509Certificate
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "testpass".toCharArray()) }
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        server = MockWebServer()
        server.useHttps(ctx.socketFactory, false)
        server.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    @After fun tearDown() { server.shutdown(); dir.deleteRecursively() }

    // The LAN guard only admits private addresses; loopback stands in for the PC here, so test the
    // TLS layer through a client whose URL host is loopback but whose pin logic is the real one.
    private fun client(pin: String) = BridgeClient(
        Pairing("https://127.0.0.1:${server.port}", "t", "hrb_abcdefghijklmnopqrstuvwxyz", pin = pin),
        hostGuard = { _, _ -> true })

    @Test fun rightPinConnectsAndSendsToken() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"device":{"id":"x","name":"t"}}"""))
        val me = client(CertPin.of(cert)).me()
        assertEquals("t", me.getJSONObject("device").getString("name"))
        val req = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer hrb_abcdefghijklmnopqrstuvwxyz", req.getHeader("Authorization"))
    }

    @Test fun wrongPinIsRefusedBeforeAnyRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        val wrong = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val failed = runCatching { client(wrong).me() }
        assertTrue(failed.exceptionOrNull() is javax.net.ssl.SSLException)
        assertFalse(client(wrong).reachable())
        assertEquals(0, server.requestCount)  // nothing — so no token — reached the server
    }

    @Test fun pinFormatMatchesTheBridge() {
        // base64url, unpadded, 43 chars: identical to the bridge's tls.cert_pin().
        val pin = CertPin.of(cert)
        assertEquals(43, pin.length)
        assertTrue(pin.matches(Regex("[A-Za-z0-9_-]+")))
    }
}
