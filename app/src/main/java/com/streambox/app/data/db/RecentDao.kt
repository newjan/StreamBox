package com.streambox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recent: RecentEntity)

    @Query(
        "DELETE FROM recents WHERE channelKey NOT IN " +
            "(SELECT channelKey FROM recents ORDER BY playedAt DESC LIMIT :keep)"
    )
    suspend fun prune(keep: Int)

    @Transaction
    suspend fun touch(key: String, now: Long, keep: Int = 30) {
        insert(RecentEntity(key, now))
        prune(keep)
    }

    @Query(
        "SELECT c.*, (f.channelKey IS NOT NULL) AS isFavorite, h.status AS healthStatus " +
            "FROM recents r " +
            "JOIN channels c ON c.`key` = r.channelKey " +
            "LEFT JOIN favorites f ON f.channelKey = c.`key` " +
            "LEFT JOIN channel_health h ON h.channelKey = c.`key` " +
            "ORDER BY r.playedAt DESC LIMIT :limit"
    )
    fun recents(limit: Int): Flow<List<ChannelWithState>>

    @Query("DELETE FROM recents")
    suspend fun clearAll()
}
