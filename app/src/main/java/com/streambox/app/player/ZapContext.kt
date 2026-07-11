package com.streambox.app.player

/**
 * The browse context playback was launched from. Zapping (channel up/down in
 * the player) pages through the same filtered, name-ordered channel list the
 * user was looking at.
 */
data class ZapContext(
    val query: String = "",
    val category: String? = null,
    val country: String? = null,
    val favoritesOnly: Boolean = false,
) {
    fun encode(): String = listOf(
        query,
        category.orEmpty(),
        country.orEmpty(),
        if (favoritesOnly) "1" else "0",
    ).joinToString(SEP)

    companion object {
        private const val SEP = ""

        fun decode(encoded: String?): ZapContext {
            if (encoded.isNullOrBlank()) return ZapContext()
            val parts = encoded.split(SEP)
            return ZapContext(
                query = parts.getOrElse(0) { "" },
                category = parts.getOrNull(1)?.takeIf(String::isNotEmpty),
                country = parts.getOrNull(2)?.takeIf(String::isNotEmpty),
                favoritesOnly = parts.getOrNull(3) == "1",
            )
        }
    }
}
