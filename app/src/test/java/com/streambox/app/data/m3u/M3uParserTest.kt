package com.streambox.app.data.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val parser = M3uParser()

    private fun parse(text: String): List<ParsedChannel> =
        parser.parse(text.lineSequence()).toList()

    @Test
    fun `parses well-formed entry`() {
        val out = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="News.us" tvg-logo="http://x/l.png" group-title="News",Newsy
            http://example.com/stream.m3u8
            """.trimIndent()
        )
        assertEquals(1, out.size)
        with(out[0]) {
            assertEquals("Newsy", name)
            assertEquals("News.us", tvgId)
            assertEquals("http://x/l.png", logoUrl)
            assertEquals("News", category)
            assertEquals("http://example.com/stream.m3u8", url)
            assertEquals("US", country)
        }
    }

    @Test
    fun `parses multiple entries`() {
        val out = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="A.fr" group-title="Kids",Chan A
            http://a.example/a.m3u8
            #EXTINF:-1 tvg-id="B.jp" group-title="Movies",Chan B
            http://b.example/b.ts
            """.trimIndent()
        )
        assertEquals(2, out.size)
        assertEquals("Chan A", out[0].name)
        assertEquals("FR", out[0].country)
        assertEquals("Chan B", out[1].name)
        assertEquals("JP", out[1].country)
    }

    @Test
    fun `skips extinf without url and continues`() {
        val out = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="A.us",Chan A
            http://a.example/a.m3u8
            #EXTINF:-1 tvg-id="Dead.us",Dead Channel
            #EXTINF:-1 tvg-id="B.us",Chan B
            http://b.example/b.m3u8
            """.trimIndent()
        )
        assertEquals(2, out.size)
        assertEquals("Chan A", out[0].name)
        assertEquals("Chan B", out[1].name)
    }

    @Test
    fun `handles missing attributes`() {
        val out = parse(
            """
            #EXTINF:-1,Bare Channel
            http://bare.example/live
            """.trimIndent()
        )
        assertEquals(1, out.size)
        with(out[0]) {
            assertEquals("Bare Channel", name)
            assertNull(tvgId)
            assertNull(logoUrl)
            assertNull(category)
            assertNull(country)
        }
    }

    @Test
    fun `handles comma inside quoted attribute`() {
        val out = parse(
            """
            #EXTINF:-1 group-title="News, Politics" tvg-id="X.de",The Channel
            http://x.example/x.m3u8
            """.trimIndent()
        )
        assertEquals(1, out.size)
        assertEquals("The Channel", out[0].name)
        assertEquals("News, Politics", out[0].category)
    }

    @Test
    fun `skips garbage lines without crashing`() {
        val out = parse(
            """
            random garbage %%%%
            #EXTINF
            http://orphan.example/url-without-extinf
            #EXTINF:-1 tvg-id="Ok.us",Good One
            http://good.example/g.m3u8
            ####
            """.trimIndent()
        )
        assertEquals(1, out.size)
        assertEquals("Good One", out[0].name)
    }

    @Test
    fun `entry with empty name falls back to url`() {
        val out = parse(
            """
            #EXTINF:-1 tvg-id="X.us",
            http://noname.example/s.m3u8
            """.trimIndent()
        )
        assertEquals(1, out.size)
        assertEquals("http://noname.example/s.m3u8", out[0].name)
    }

    @Test
    fun `blank tvg id yields null country`() {
        val out = parse(
            """
            #EXTINF:-1 tvg-id="",No Id
            http://noid.example/s.m3u8
            """.trimIndent()
        )
        assertEquals(1, out.size)
        assertNull(out[0].tvgId)
        assertNull(out[0].country)
    }

    @Test
    fun `parses header tvg url`() {
        assertEquals(
            "http://e/g.xml",
            parser.parseHeader("#EXTM3U x-tvg-url=\"http://e/g.xml\"")
        )
        assertEquals(
            "http://e/g2.xml",
            parser.parseHeader("#EXTM3U url-tvg=\"http://e/g2.xml\"")
        )
        assertNull(parser.parseHeader("#EXTM3U"))
        assertNull(parser.parseHeader("#EXTINF:-1,Name"))
    }

    @Test
    fun `blank playlist yields empty`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("\n\n\n").isEmpty())
    }
}
