package com.streambox.app.ui.shared

/**
 * Converts an ISO 3166-1 alpha-2 code to its flag emoji, or empty when the
 * code isn't two ASCII letters.
 */
fun countryFlagEmoji(code: String): String {
    val cc = code.trim().uppercase()
    if (cc.length != 2 || cc.any { it !in 'A'..'Z' }) return ""
    return cc.map { Character.toChars(0x1F1E6 + (it - 'A')) }
        .joinToString("") { String(it) }
}
