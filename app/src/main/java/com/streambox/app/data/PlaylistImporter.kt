package com.streambox.app.data

import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelEntity
import com.streambox.app.data.db.ChannelKey
import com.streambox.app.data.m3u.M3uParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class ImportProgress {
    data class Running(val count: Int) : ImportProgress()
    data class Done(val count: Int) : ImportProgress()
    data class Failed(val message: String) : ImportProgress()
}

/**
 * Streams playlist lines into Room in batches under a fresh [ChannelEntity.generation].
 * Old rows are only deleted after the whole import succeeds, so a failed or
 * empty refresh never destroys the existing cache.
 */
class PlaylistImporter(
    private val parser: M3uParser,
    private val channelDao: ChannelDao,
) {
    fun import(
        lines: Sequence<String>,
        generation: Long,
        onEpgUrl: suspend (String?) -> Unit,
    ): Flow<ImportProgress> = flow {
        var count = 0
        var epgUrl: String? = null
        try {
            var headerSeen = false
            val batch = ArrayList<ChannelEntity>(BATCH_SIZE)
            val channels = parser.parse(
                lines.onEach { line ->
                    if (!headerSeen && line.startsWith("#EXTM3U", ignoreCase = true)) {
                        headerSeen = true
                        epgUrl = parser.parseHeader(line)
                    }
                }
            )
            for (parsed in channels) {
                batch += ChannelEntity(
                    key = ChannelKey.of(parsed.url),
                    name = parsed.name,
                    url = parsed.url,
                    tvgId = parsed.tvgId,
                    logoUrl = parsed.logoUrl,
                    category = parsed.category,
                    country = parsed.country,
                    generation = generation,
                )
                if (batch.size >= BATCH_SIZE) {
                    channelDao.insertAll(batch.toList())
                    count += batch.size
                    batch.clear()
                    emit(ImportProgress.Running(count))
                }
            }
            if (batch.isNotEmpty()) {
                channelDao.insertAll(batch.toList())
                count += batch.size
                emit(ImportProgress.Running(count))
            }
            if (count == 0) {
                emit(ImportProgress.Failed("No channels found in playlist"))
                return@flow
            }
            channelDao.deleteOtherGenerations(generation)
            onEpgUrl(epgUrl)
            emit(ImportProgress.Done(count))
        } catch (t: Exception) {
            runCatching { channelDao.deleteGeneration(generation) }
            emit(ImportProgress.Failed(t.message ?: "Playlist download failed"))
        }
    }

    private companion object {
        const val BATCH_SIZE = 500
    }
}
