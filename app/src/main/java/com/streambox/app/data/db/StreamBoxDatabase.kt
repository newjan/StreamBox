package com.streambox.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChannelEntity::class,
        FavoriteEntity::class,
        RecentEntity::class,
        ProgrammeEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class StreamBoxDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentDao(): RecentDao
    abstract fun programmeDao(): ProgrammeDao
}
