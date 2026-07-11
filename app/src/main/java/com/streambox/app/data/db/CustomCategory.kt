package com.streambox.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A user-created channel list ("custom category"). Name only, by design. */
@Entity(
    tableName = "custom_categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "custom_category_channels",
    primaryKeys = ["categoryId", "channelKey"],
    indices = [Index("channelKey")],
)
data class CustomCategoryChannelEntity(
    val categoryId: Long,
    val channelKey: String,
)

data class CustomCategoryWithCount(val id: Long, val name: String, val count: Int)

@Dao
interface CustomCategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CustomCategoryEntity): Long

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM custom_category_channels WHERE categoryId = :id")
    suspend fun deleteMembers(id: Long)

    @Transaction
    suspend fun deleteWithMembers(id: Long) {
        deleteMembers(id)
        deleteCategory(id)
    }

    @Query(
        "SELECT c.id AS id, c.name AS name, COUNT(m.channelKey) AS count " +
            "FROM custom_categories c " +
            "LEFT JOIN custom_category_channels m ON m.categoryId = c.id " +
            "GROUP BY c.id ORDER BY c.name COLLATE NOCASE"
    )
    fun categoriesWithCounts(): Flow<List<CustomCategoryWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addChannel(member: CustomCategoryChannelEntity)

    @Query(
        "DELETE FROM custom_category_channels " +
            "WHERE categoryId = :categoryId AND channelKey = :channelKey"
    )
    suspend fun removeChannel(categoryId: Long, channelKey: String)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM custom_category_channels " +
            "WHERE categoryId = :categoryId AND channelKey = :channelKey)"
    )
    suspend fun contains(categoryId: Long, channelKey: String): Boolean

    @Transaction
    suspend fun toggle(categoryId: Long, channelKey: String) {
        if (contains(categoryId, channelKey)) {
            removeChannel(categoryId, channelKey)
        } else {
            addChannel(CustomCategoryChannelEntity(categoryId, channelKey))
        }
    }

    @Query("SELECT categoryId FROM custom_category_channels WHERE channelKey = :channelKey")
    fun categoryIdsFor(channelKey: String): Flow<List<Long>>

    @Query(
        "SELECT c.*, (f.channelKey IS NOT NULL) AS isFavorite, h.status AS healthStatus " +
            "FROM custom_category_channels m " +
            "JOIN channels c ON c.`key` = m.channelKey " +
            "LEFT JOIN favorites f ON f.channelKey = c.`key` " +
            "LEFT JOIN channel_health h ON h.channelKey = c.`key` " +
            "WHERE m.categoryId = :categoryId " +
            "ORDER BY c.name COLLATE NOCASE, c.`key` LIMIT :limit"
    )
    fun channelsFor(categoryId: Long, limit: Int): Flow<List<ChannelWithState>>

    @Query("DELETE FROM custom_category_channels")
    suspend fun clearAllMembers()
}
