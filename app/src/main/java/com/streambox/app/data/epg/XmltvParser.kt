package com.streambox.app.data.epg

import com.streambox.app.data.db.ProgrammeEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Streaming XMLTV parser. Only `programme` elements overlapping the given
 * time window are emitted, so multi-day national guides stay memory-bounded.
 * Malformed programmes (or a truncated document) are skipped silently.
 */
class XmltvParser {

    fun parse(
        input: InputStream,
        windowStartMs: Long,
        windowEndMs: Long,
        onProgramme: (ProgrammeEntity) -> Unit,
    ) {
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(input, null)

            var tvgId: String? = null
            var startMs: Long? = null
            var stopMs: Long? = null
            var title: String? = null
            var inProgramme = false
            var inTitle = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "programme" -> {
                            inProgramme = true
                            tvgId = parser.getAttributeValue(null, "channel")
                            startMs = parseXmltvTime(parser.getAttributeValue(null, "start"))
                            stopMs = parseXmltvTime(parser.getAttributeValue(null, "stop"))
                            title = null
                        }
                        "title" -> if (inProgramme) inTitle = true
                    }
                    XmlPullParser.TEXT -> if (inTitle && title == null) {
                        title = parser.text?.trim()?.takeIf(String::isNotEmpty)
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "title" -> inTitle = false
                        "programme" -> {
                            val id = tvgId
                            val start = startMs
                            val stop = stopMs
                            val text = title
                            if (id != null && start != null && stop != null && text != null &&
                                stop > windowStartMs && start < windowEndMs
                            ) {
                                onProgramme(ProgrammeEntity(id, start, stop, text))
                            }
                            inProgramme = false
                            inTitle = false
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Truncated/invalid XML: keep whatever was emitted so far.
        }
    }

    /** XMLTV times look like `20260711120000 +0000`; the offset may be absent. */
    private fun parseXmltvTime(raw: String?): Long? {
        val value = raw?.trim() ?: return null
        if (value.length < 14) return null
        return runCatching {
            val hasOffset = value.length > 14 && value.substring(14).isNotBlank()
            val format = if (hasOffset) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
            format.isLenient = false
            format.parse(if (hasOffset) value else value.take(14))?.time
        }.getOrNull()
    }
}
