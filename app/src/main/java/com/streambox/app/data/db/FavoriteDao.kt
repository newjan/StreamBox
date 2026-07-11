package com.streambox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE channelKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE channelKey = :key)")
    suspend fun exists(key: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE channelKey = :key)")
    fun isFavorite(key: String): Flow<Boolean>

    @Transaction
    suspend fun toggle(key: String, now: Long) {
        if (exists(key)) delete(key) else insert(FavoriteEntity(key, now))
    }

    @Query(
        "SELECT c.*, 1 AS isFavorite FROM favorites f " +
            "JOIN channels c ON c.`key` = f.channelKey " +
            "ORDER BY f.addedAt DESC LIMIT :limit"
    )
    fun favorites(limit: Int): Flow<List<ChannelWithState>>

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
