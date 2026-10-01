package com.nath.hermesremote.data

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS for the local network, trusting exactly one certificate: the bridge's, whose SHA-256 came
 * in the pairing QR code. No CA is involved, so a device that only holds the PC's old IP address
 * (another laptop on a cafe or home Wi-Fi) fails the handshake before a single request byte —
 * and therefore before the device token — is sent.
 */
object CertPin {
    /** base64url(SHA-256(DER)), unpadded: the same format the bridge prints. */
    fun of(cert: X509Certificate): String =
        java.util.Base64.getUrlEncoder().withoutPadding()  // java.util: API 26+, and unit-testable
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.encoded))

    fun trustManager(pin: String, digest: (X509Certificate) -> String = ::of): X509TrustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                throw CertificateException("client certificates are not used")

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
                if (!MessageDigest.isEqual(digest(leaf).toByteArray(), pin.toByteArray()))
                    throw CertificateException("This is not your PC's bridge (certificate mismatch)")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

    fun socketFactory(tm: X509TrustManager): SSLSocketFactory =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }.socketFactory
}
