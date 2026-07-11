package com.streambox.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChannelEntity::class,
        FavoriteEntity::class,
        RecentEntity::class,
        ProgrammeEntity::class,
        ChannelHealthEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class StreamBoxDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentDao(): RecentDao
    abstract fun programmeDao(): ProgrammeDao
    abstract fun channelHealthDao(): ChannelHealthDao

    companion object {
        /** v2 adds the channel_health table; favorites/recents must survive. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channel_health` (" +
                        "`channelKey` TEXT NOT NULL, " +
                        "`status` INTEGER NOT NULL, " +
                        "`checkedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`channelKey`))"
                )
            }
        }
    }
}
