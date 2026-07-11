package com.streambox.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest

@Entity(
    tableName = "channels",
    indices = [
        Index("name"),
        Index("category"),
        Index("country"),
        Index("generation"),
    ],
)
data class ChannelEntity(
    /** Stable identity across playlist refreshes: MD5 of the stream URL. */
    @PrimaryKey val key: String,
    val name: String,
    val url: String,
    val tvgId: String?,
    val logoUrl: String?,
    val category: String?,
    val country: String?,
    /** Import generation; rows from older generations are purged after a successful refresh. */
    val generation: Long,
)

object ChannelKey {
    fun of(url: String): String =
        MessageDigest.getInstance("MD5").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
