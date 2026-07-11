package com.streambox.app.data.m3u

data class ParsedChannel(
    val name: String,
    val url: String,
    val tvgId: String?,
    val logoUrl: String?,
    val category: String?,
    val country: String?,
)
