package com.streambox.app.data.net

import android.content.Context
import com.streambox.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS compatibility layer for old Android TV boxes with stale system CA
 * stores:
 *
 * 1. Server certificates are validated against the device store first, then
 *    against a bundled current Mozilla CA set (res/raw/cacerts.pem). The PEM
 *    is parsed manually, block by block — Android's CertificateFactory is
 *    unreliable on bundles that contain comment text between certificates.
 * 2. [trustAll] (user opt-in, "Trust all certificates" in Settings) accepts
 *    any certificate as a last resort for devices that still fail. Volatile,
 *    so toggling applies to the next connection without an app restart.
 */
@Singleton
class TlsCompat @Inject constructor(@ApplicationContext context: Context) {

    @Volatile
    var trustAll: Boolean = false

    private val compositeTrust: X509TrustManager? = runCatching {
        CompositeTrustManager(
            listOf(systemTrustManager(), bundledTrustManager(context)),
        )
    }.getOrNull()

    private val lenientTrust = LenientTrustManager()

    private val socketFactory: SSLSocketFactory? = runCatching {
        SSLContext.getInstance("TLS")
            .apply { init(null, arrayOf<X509TrustManager>(lenientTrust), null) }
            .socketFactory
    }.getOrNull()

    private val defaultHostnameVerifier: HostnameVerifier =
        HttpsURLConnection.getDefaultHostnameVerifier()

    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        socketFactory?.let { builder.sslSocketFactory(it, lenientTrust) }
        builder.hostnameVerifier { hostname, session ->
            trustAll || defaultHostnameVerifier.verify(hostname, session)
        }
        // Old TLS stacks may miss OkHttp's MODERN_TLS cipher list.
        builder.connectionSpecs(
            listOf(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT),
        )
        return builder
    }

    /** system store → bundled roots → (opt-in) trust anyway. */
    private inner class LenientTrustManager : X509TrustManager {
        private val delegate: X509TrustManager?
            get() = compositeTrust

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                delegate?.checkServerTrusted(chain, authType)
                    ?: systemTrustManager().checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                if (!trustAll) throw e
            }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                delegate?.checkClientTrusted(chain, authType)
                    ?: systemTrustManager().checkClientTrusted(chain, authType)
            } catch (e: CertificateException) {
                if (!trustAll) throw e
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            delegate?.acceptedIssuers ?: emptyArray()
    }

    private fun bundledTrustManager(context: Context): X509TrustManager {
        val certificates = context.resources.openRawResource(R.raw.cacerts)
            .use(PemReader::readCertificates)
        check(certificates.isNotEmpty()) { "Bundled CA set is empty" }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            certificates.forEachIndexed { i, cert -> setCertificateEntry("ca$i", cert) }
        }
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }

    private companion object {
        fun systemTrustManager(): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as KeyStore?) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .first()
    }

    private class CompositeTrustManager(
        private val delegates: List<X509TrustManager>,
    ) : X509TrustManager {

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            var lastError: CertificateException? = null
            for (delegate in delegates) {
                try {
                    delegate.checkServerTrusted(chain, authType)
                    return
                } catch (e: CertificateException) {
                    lastError = e
                }
            }
            throw lastError ?: CertificateException("No trust managers available")
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            var lastError: CertificateException? = null
            for (delegate in delegates) {
                try {
                    delegate.checkClientTrusted(chain, authType)
                    return
                } catch (e: CertificateException) {
                    lastError = e
                }
            }
            throw lastError ?: CertificateException("No trust managers available")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            delegates.flatMap { it.acceptedIssuers.asList() }.toTypedArray()
    }
}

/**
 * Splits a PEM bundle into BEGIN/END CERTIFICATE blocks and decodes each one
 * individually. Provider-independent: comment text between blocks (as in the
 * Mozilla bundle) can't derail it the way it derails Android's
 * CertificateFactory.generateCertificates.
 */
object PemReader {
    private const val BEGIN = "-----BEGIN CERTIFICATE-----"
    private const val END = "-----END CERTIFICATE-----"

    fun readCertificates(input: InputStream): List<X509Certificate> {
        val text = input.bufferedReader().readText()
        val factory = CertificateFactory.getInstance("X.509")
        val out = ArrayList<X509Certificate>()
        var index = 0
        while (true) {
            val begin = text.indexOf(BEGIN, index)
            if (begin < 0) break
            val end = text.indexOf(END, begin)
            if (end < 0) break
            val base64 = text.substring(begin + BEGIN.length, end)
                .filterNot(Char::isWhitespace)
            runCatching {
                // java.util.Base64 comes from core-library desugaring on minSdk 23.
                val der = java.util.Base64.getDecoder().decode(base64)
                out += factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            }
            index = end + END.length
        }
        return out
    }
}
