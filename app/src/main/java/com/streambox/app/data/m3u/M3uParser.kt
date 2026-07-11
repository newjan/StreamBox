package com.streambox.app.data.m3u

/**
 * Streaming M3U/EXTM3U parser. Operates on a line [Sequence] so the whole
 * playlist is never held in memory. Malformed input is skipped, never thrown.
 */
class M3uParser {

    private val attrRegex = Regex("([a-zA-Z0-9-]+)=\"(.*?)\"")
    private val countryFromTvgId = Regex("\\.([a-z]{2})$", RegexOption.IGNORE_CASE)

    fun parse(lines: Sequence<String>): Sequence<ParsedChannel> = sequence {
        var pendingExtinf: String? = null
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingExtinf = line
                line.startsWith("#") -> Unit // header, EXTVLCOPT, comments…
                else -> {
                    val extinf = pendingExtinf
                    pendingExtinf = null
                    if (extinf != null && looksLikeUrl(line)) {
                        toChannel(extinf, line)?.let { yield(it) }
                    }
                }
            }
        }
    }

    /** Returns the XMLTV guide URL from an `#EXTM3U` header line, if present. */
    fun parseHeader(line: String): String? {
        if (!line.trim().startsWith("#EXTM3U", ignoreCase = true)) return null
        val attrs = attrRegex.findAll(line).associate {
            it.groupValues[1].lowercase() to it.groupValues[2]
        }
        return attrs["x-tvg-url"]?.takeIf { it.isNotBlank() }
            ?: attrs["url-tvg"]?.takeIf { it.isNotBlank() }
    }

    private fun looksLikeUrl(line: String): Boolean =
        line.contains("://") && !line.contains(' ')

    private fun toChannel(extinf: String, url: String): ParsedChannel? = runCatching {
        val attrs = attrRegex.findAll(extinf).associate {
            it.groupValues[1].lowercase() to it.groupValues[2]
        }
        // An EXTINF with no name-separating comma at all is malformed.
        val name = displayName(extinf)?.ifBlank { url } ?: return null
        val tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
        ParsedChannel(
            name = name,
            url = url,
            tvgId = tvgId,
            logoUrl = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            category = attrs["group-title"]?.takeIf { it.isNotBlank() },
            country = tvgId?.let { id ->
                countryFromTvgId.find(id)?.groupValues?.get(1)?.uppercase()
            },
        )
    }.getOrNull()

    /**
     * The display name is everything after the comma that terminates the
     * duration/attribute list. Attributes may contain commas inside quotes and
     * the name itself may contain commas, so take the first comma that sits
     * outside any quoted region.
     */
    private fun displayName(extinf: String): String? {
        var inQuotes = false
        for (i in extinf.indices) {
            when (extinf[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return extinf.substring(i + 1).trim()
            }
        }
        return null
    }
}
