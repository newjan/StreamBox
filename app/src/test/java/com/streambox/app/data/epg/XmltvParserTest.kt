package com.streambox.app.data.epg

import com.streambox.app.data.db.ProgrammeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class XmltvParserTest {

    private val parser = XmltvParser()

    // 2026-07-11 12:00:00 UTC
    private val noon = 1783771200000L
    private val hour = 3_600_000L

    private fun parse(xml: String, windowStart: Long, windowEnd: Long): List<ProgrammeEntity> {
        val out = mutableListOf<ProgrammeEntity>()
        parser.parse(ByteArrayInputStream(xml.toByteArray()), windowStart, windowEnd) { out += it }
        return out
    }

    @Test
    fun `parses programmes with titles and times`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260711120000 +0000" stop="20260711130000 +0000" channel="News.us">
                <title lang="en">Midday News</title>
              </programme>
              <programme start="20260711130000 +0000" stop="20260711140000 +0000" channel="News.us">
                <title>Afternoon Show</title>
              </programme>
            </tv>
        """.trimIndent()
        val out = parse(xml, noon - hour, noon + 12 * hour)
        assertEquals(2, out.size)
        assertEquals("Midday News", out[0].title)
        assertEquals("News.us", out[0].tvgId)
        assertEquals(noon, out[0].startMs)
        assertEquals(noon + hour, out[0].stopMs)
        assertEquals("Afternoon Show", out[1].title)
    }

    @Test
    fun `skips programmes outside the window`() {
        val xml = """
            <tv>
              <programme start="20260701120000 +0000" stop="20260701130000 +0000" channel="Old.us">
                <title>Way in the past</title>
              </programme>
              <programme start="20260711120000 +0000" stop="20260711130000 +0000" channel="News.us">
                <title>Current</title>
              </programme>
            </tv>
        """.trimIndent()
        val out = parse(xml, noon - hour, noon + 12 * hour)
        assertEquals(1, out.size)
        assertEquals("Current", out[0].title)
    }

    @Test
    fun `skips malformed programmes and continues`() {
        val xml = """
            <tv>
              <programme start="garbage" stop="alsogarbage" channel="Bad.us">
                <title>Broken times</title>
              </programme>
              <programme start="20260711120000 +0000" stop="20260711130000 +0000">
                <title>No channel attr</title>
              </programme>
              <programme start="20260711120000 +0000" stop="20260711130000 +0000" channel="Ok.us">
                <title>Survives</title>
              </programme>
            </tv>
        """.trimIndent()
        val out = parse(xml, noon - hour, noon + 12 * hour)
        assertEquals(1, out.size)
        assertEquals("Survives", out[0].title)
    }

    @Test
    fun `handles timezone offsets`() {
        val xml = """
            <tv>
              <programme start="20260711140000 +0200" stop="20260711150000 +0200" channel="X.de">
                <title>Offset show</title>
              </programme>
            </tv>
        """.trimIndent()
        val out = parse(xml, noon - hour, noon + 12 * hour)
        assertEquals(1, out.size)
        assertEquals(noon, out[0].startMs)
    }

    @Test
    fun `broken xml yields no results without crashing`() {
        val out = parse("<tv><programme start=", 0, Long.MAX_VALUE)
        assertTrue(out.isEmpty())
    }
}
