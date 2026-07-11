package com.streambox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgrammeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes WHERE stopMs < :beforeMs")
    suspend fun pruneEndedBefore(beforeMs: Long)

    @Query(
        "SELECT title FROM programmes " +
            "WHERE tvgId = :tvgId AND startMs <= :nowMs AND stopMs > :nowMs " +
            "ORDER BY startMs DESC LIMIT 1"
    )
    fun nowPlaying(tvgId: String, nowMs: Long): Flow<String?>

    @Query(
        "SELECT p.tvgId AS tvgId, p.title AS title FROM programmes p " +
            "WHERE p.startMs <= :nowMs AND p.stopMs > :nowMs"
    )
    fun nowTitles(nowMs: Long): Flow<List<NowTitle>>

    @Query("DELETE FROM programmes")
    suspend fun clearAll()
}

data class NowTitle(val tvgId: String, val title: String)
