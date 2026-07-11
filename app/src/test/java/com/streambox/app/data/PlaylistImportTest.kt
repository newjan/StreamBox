package com.streambox.app.data

import androidx.paging.PagingSource
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelEntity
import com.streambox.app.data.db.ChannelWithState
import com.streambox.app.data.db.KeyUrl
import com.streambox.app.data.m3u.M3uParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeChannelDao : ChannelDao {
    val insertBatches = mutableListOf<List<ChannelEntity>>()
    var deletedOtherThan: Long? = null
    var deletedGeneration: Long? = null

    override suspend fun insertAll(items: List<ChannelEntity>) {
        insertBatches += items
    }

    override suspend fun deleteOtherGenerations(generation: Long) {
        deletedOtherThan = generation
    }

    override suspend fun deleteGeneration(generation: Long) {
        deletedGeneration = generation
    }

    override suspend fun maxGeneration(): Long? = 7L
    override suspend fun count(): Int = insertBatches.sumOf { it.size }

    override fun pagingSource(
        query: String, category: String?, country: String?, favoritesOnly: Boolean, hideDead: Boolean,
    ): PagingSource<Int, ChannelWithState> = throw UnsupportedOperationException()
    override fun byKey(key: String): Flow<ChannelWithState?> = throw UnsupportedOperationException()
    override suspend fun byKeyOnce(key: String): ChannelWithState? = throw UnsupportedOperationException()
    override suspend fun nextAfter(
        name: String, key: String, query: String, category: String?, country: String?,
        favoritesOnly: Boolean, hideDead: Boolean,
    ): ChannelWithState? = throw UnsupportedOperationException()
    override suspend fun prevBefore(
        name: String, key: String, query: String, category: String?, country: String?,
        favoritesOnly: Boolean, hideDead: Boolean,
    ): ChannelWithState? = throw UnsupportedOperationException()
    override suspend fun first(
        query: String, category: String?, country: String?, favoritesOnly: Boolean, hideDead: Boolean,
    ): ChannelWithState? = throw UnsupportedOperationException()
    override suspend fun last(
        query: String, category: String?, country: String?, favoritesOnly: Boolean, hideDead: Boolean,
    ): ChannelWithState? = throw UnsupportedOperationException()
    override fun categories(): Flow<List<String>> = throw UnsupportedOperationException()
    override fun countries(): Flow<List<String>> = throw UnsupportedOperationException()
    override fun channelsForCategory(
        category: String, favoritesOnly: Boolean, hideDead: Boolean, limit: Int,
    ): Flow<List<ChannelWithState>> = throw UnsupportedOperationException()
    override fun channelsForCountry(
        country: String, favoritesOnly: Boolean, hideDead: Boolean, limit: Int,
    ): Flow<List<ChannelWithState>> = throw UnsupportedOperationException()
    override suspend fun keyUrls(): List<KeyUrl> = throw UnsupportedOperationException()
    override fun countFlow(): Flow<Int> = throw UnsupportedOperationException()
    override suspend fun clearAll() = throw UnsupportedOperationException()
}

class PlaylistImportTest {

    private fun playlistLines(count: Int): Sequence<String> = sequence {
        yield("#EXTM3U x-tvg-url=\"http://guide.example/epg.xml\"")
        for (i in 0 until count) {
            yield("#EXTINF:-1 tvg-id=\"Ch$i.us\" group-title=\"G\",Channel $i")
            yield("http://stream.example/$i.m3u8")
        }
    }

    private fun importer(dao: FakeChannelDao) = PlaylistImporter(M3uParser(), dao)

    @Test
    fun `imports in batches and swaps generation on success`() = runTest {
        val dao = FakeChannelDao()
        var epgUrl: String? = null
        val events = importer(dao).import(playlistLines(1200), generation = 8L) { epgUrl = it }.toList()

        assertEquals(listOf(500, 500, 200), dao.insertBatches.map { it.size })
        assertTrue(dao.insertBatches.flatten().all { it.generation == 8L })
        assertEquals(8L, dao.deletedOtherThan)
        assertEquals(null, dao.deletedGeneration)
        assertEquals("http://guide.example/epg.xml", epgUrl)
        assertEquals(ImportProgress.Done(1200), events.last())
        val counts = events.filterIsInstance<ImportProgress.Running>().map { it.count }
        assertEquals(counts, counts.sorted())
        assertTrue(counts.isNotEmpty())
    }

    @Test
    fun `mid-stream failure keeps old cache and removes partial import`() = runTest {
        val dao = FakeChannelDao()
        val lines = sequence {
            yieldAll(playlistLines(600))
            throw IOException("connection reset")
        }
        val events = importer(dao).import(lines, generation = 8L) {}.toList()

        assertTrue(events.last() is ImportProgress.Failed)
        assertEquals(null, dao.deletedOtherThan)
        assertEquals(8L, dao.deletedGeneration)
    }

    @Test
    fun `empty playlist does not wipe existing cache`() = runTest {
        val dao = FakeChannelDao()
        val events = importer(dao).import(sequenceOf("#EXTM3U"), generation = 8L) {}.toList()

        assertTrue(events.last() is ImportProgress.Failed)
        assertEquals(null, dao.deletedOtherThan)
        assertFalse(dao.insertBatches.flatten().isNotEmpty())
    }
}
