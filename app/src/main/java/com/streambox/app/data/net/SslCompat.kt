package com.streambox.app.data.net

import android.content.Context
import com.streambox.app.R
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Old Android TV boxes ship outdated system CA stores and never see updates,
 * so modern chains (e.g. github.io, Let's Encrypt) fail with
 * "Trust anchor for certification path not found".
 *
 * This installs a trust manager that consults the device store first and
 * falls back to a bundled current Mozilla CA set (res/raw/cacerts.pem).
 * Certificates the device already trusts keep working exactly as before.
 */
object SslCompat {

    fun apply(builder: OkHttpClient.Builder, context: Context): OkHttpClient.Builder =
        runCatching {
            val composite = CompositeTrustManager(
                listOf(systemTrustManager(), bundledTrustManager(context)),
            )
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(composite), null)
            }
            builder.sslSocketFactory(sslContext.socketFactory, composite)
        }.getOrDefault(builder) // any failure → stock platform behavior

    private fun systemTrustManager(): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    private fun bundledTrustManager(context: Context): X509TrustManager {
        val certificates = context.resources.openRawResource(R.raw.cacerts).use {
            CertificateFactory.getInstance("X.509").generateCertificates(it)
        }
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
