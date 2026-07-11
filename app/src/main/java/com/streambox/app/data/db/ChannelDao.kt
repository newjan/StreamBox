package com.streambox.app.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val WITH_FAV =
    "SELECT c.*, (f.channelKey IS NOT NULL) AS isFavorite FROM channels c " +
        "LEFT JOIN favorites f ON f.channelKey = c.`key`"

private const val FILTER =
    "(:query = '' OR c.name LIKE '%' || :query || '%') " +
        "AND (:category IS NULL OR c.category = :category) " +
        "AND (:country IS NULL OR c.country = :country) " +
        "AND (:favoritesOnly = 0 OR f.channelKey IS NOT NULL)"

@Dao
interface ChannelDao {

    @Query("$WITH_FAV WHERE $FILTER ORDER BY c.name COLLATE NOCASE, c.`key`")
    fun pagingSource(
        query: String,
        category: String?,
        country: String?,
        favoritesOnly: Boolean,
    ): PagingSource<Int, ChannelWithState>

    @Query("$WITH_FAV WHERE c.`key` = :key")
    fun byKey(key: String): Flow<ChannelWithState?>

    @Query("$WITH_FAV WHERE c.`key` = :key")
    suspend fun byKeyOnce(key: String): ChannelWithState?

    /** Next channel after (name, key) within the same filter; used for zapping. */
    @Query(
        "$WITH_FAV WHERE $FILTER AND " +
            "(c.name COLLATE NOCASE > :name OR (c.name COLLATE NOCASE = :name AND c.`key` > :key)) " +
            "ORDER BY c.name COLLATE NOCASE, c.`key` LIMIT 1"
    )
    suspend fun nextAfter(
        name: String,
        key: String,
        query: String,
        category: String?,
        country: String?,
        favoritesOnly: Boolean,
    ): ChannelWithState?

    /** Previous channel before (name, key) within the same filter; used for zapping. */
    @Query(
        "$WITH_FAV WHERE $FILTER AND " +
            "(c.name COLLATE NOCASE < :name OR (c.name COLLATE NOCASE = :name AND c.`key` < :key)) " +
            "ORDER BY c.name COLLATE NOCASE DESC, c.`key` DESC LIMIT 1"
    )
    suspend fun prevBefore(
        name: String,
        key: String,
        query: String,
        category: String?,
        country: String?,
        favoritesOnly: Boolean,
    ): ChannelWithState?

    /** First channel in the filter (wrap-around target for next). */
    @Query("$WITH_FAV WHERE $FILTER ORDER BY c.name COLLATE NOCASE, c.`key` LIMIT 1")
    suspend fun first(
        query: String,
        category: String?,
        country: String?,
        favoritesOnly: Boolean,
    ): ChannelWithState?

    /** Last channel in the filter (wrap-around target for previous). */
    @Query("$WITH_FAV WHERE $FILTER ORDER BY c.name COLLATE NOCASE DESC, c.`key` DESC LIMIT 1")
    suspend fun last(
        query: String,
        category: String?,
        country: String?,
        favoritesOnly: Boolean,
    ): ChannelWithState?

    @Query(
        "SELECT DISTINCT category FROM channels " +
            "WHERE category IS NOT NULL AND category != '' ORDER BY category COLLATE NOCASE"
    )
    fun categories(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT country FROM channels " +
            "WHERE country IS NOT NULL AND country != '' ORDER BY country"
    )
    fun countries(): Flow<List<String>>

    @Query(
        "$WITH_FAV WHERE c.category = :category " +
            "ORDER BY c.name COLLATE NOCASE, c.`key` LIMIT :limit"
    )
    fun channelsForCategory(category: String, limit: Int): Flow<List<ChannelWithState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE generation != :generation")
    suspend fun deleteOtherGenerations(generation: Long)

    @Query("DELETE FROM channels WHERE generation = :generation")
    suspend fun deleteGeneration(generation: Long)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM channels")
    fun countFlow(): Flow<Int>

    @Query("SELECT MAX(generation) FROM channels")
    suspend fun maxGeneration(): Long?

    @Query("DELETE FROM channels")
    suspend fun clearAll()
}
