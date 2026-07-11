package com.streambox.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

object HealthStatus {
    const val UNKNOWN = 0
    const val OK = 1
    const val DEAD = 2
}

/**
 * Last known reachability of a stream. Filled in by the background scanner
 * and passively by the player (successful playback → OK, error → DEAD).
 */
@Entity(tableName = "channel_health")
data class ChannelHealthEntity(
    @PrimaryKey val channelKey: String,
    val status: Int,
    val checkedAt: Long,
)

@Dao
interface ChannelHealthDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(health: ChannelHealthEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChannelHealthEntity>)

    @Query("SELECT channelKey FROM channel_health WHERE checkedAt > :sinceMs")
    suspend fun checkedSince(sinceMs: Long): List<String>

    @Query("SELECT COUNT(*) FROM channel_health WHERE status = 1")
    fun workingCount(): Flow<Int>

    @Query("DELETE FROM channel_health")
    suspend fun clearAll()
}
