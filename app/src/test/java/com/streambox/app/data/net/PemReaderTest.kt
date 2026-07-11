package com.streambox.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class PemReaderTest {

    @Test
    fun `parses the real bundled Mozilla CA set completely`() {
        // Unit tests run from the module directory.
        val pem = File("src/main/res/raw/cacerts.pem")
        assertTrue("bundled cacerts.pem missing", pem.exists())

        val expected = pem.readText().windowed("-----BEGIN CERTIFICATE-----".length)
            .count { it == "-----BEGIN CERTIFICATE-----" }
        val certs = pem.inputStream().use(PemReader::readCertificates)

        assertEquals(expected, certs.size)
        assertTrue(certs.size > 100)
        // Modern roots old boxes are missing must be present.
        assertTrue(certs.any { it.subjectX500Principal.name.contains("ISRG Root X1") })
    }

    @Test
    fun `comment text between certificates does not derail parsing`() {
        val one = File("src/main/res/raw/cacerts.pem").readText()
            .substringAfter("-----BEGIN CERTIFICATE-----")
            .substringBefore("-----END CERTIFICATE-----")
        val doc = """
            # Mozilla bundle style header
            Some Root CA
            ============
            -----BEGIN CERTIFICATE-----$one-----END CERTIFICATE-----
            random trailing commentary
            -----BEGIN CERTIFICATE-----$one-----END CERTIFICATE-----
        """.trimIndent()
        val certs = PemReader.readCertificates(ByteArrayInputStream(doc.toByteArray()))
        assertEquals(2, certs.size)
    }

    @Test
    fun `garbage input yields empty list without crashing`() {
        val certs = PemReader.readCertificates(ByteArrayInputStream("not a pem".toByteArray()))
        assertTrue(certs.isEmpty())
    }
}
