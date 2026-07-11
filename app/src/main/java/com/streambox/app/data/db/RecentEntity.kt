package com.streambox.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val channelKey: String,
    val playedAt: Long,
)
