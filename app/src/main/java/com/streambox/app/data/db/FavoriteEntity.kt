package com.streambox.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val channelKey: String,
    val addedAt: Long,
)
