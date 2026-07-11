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
        CustomCategoryEntity::class,
        CustomCategoryChannelEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class StreamBoxDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentDao(): RecentDao
    abstract fun programmeDao(): ProgrammeDao
    abstract fun channelHealthDao(): ChannelHealthDao
    abstract fun customCategoryDao(): CustomCategoryDao

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

        /** v3 adds user-created custom categories and their membership table. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_categories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_categories_name` " +
                        "ON `custom_categories` (`name`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_category_channels` (" +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`channelKey` TEXT NOT NULL, " +
                        "PRIMARY KEY(`categoryId`, `channelKey`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_category_channels_channelKey` " +
                        "ON `custom_category_channels` (`channelKey`)"
                )
            }
        }
    }
}
