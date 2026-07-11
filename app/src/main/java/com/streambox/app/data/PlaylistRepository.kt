package com.streambox.app.data

import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.FavoriteDao
import com.streambox.app.data.db.ProgrammeDao
import com.streambox.app.data.db.RecentDao
import com.streambox.app.data.m3u.M3uParser
import com.streambox.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.cert.CertificateException
import java.text.DateFormat
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

@Singleton
class PlaylistRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    parser: M3uParser,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val recentDao: RecentDao,
    private val programmeDao: ProgrammeDao,
    private val settings: SettingsRepository,
) {
    private val importer = PlaylistImporter(parser, channelDao)

    /**
     * Downloads the configured playlist and streams it into the channel cache.
     * Emits [ImportProgress.Running] with a growing count, then Done or Failed.
     * The existing cache stays intact unless the import fully succeeds.
     */
    fun refresh(): Flow<ImportProgress> = flow {
        val url = settings.playlistUrl.first()
        val generation = (channelDao.maxGeneration() ?: 0L) + 1L
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ImportProgress.Failed("Playlist download failed (HTTP ${response.code})"))
                    return@flow
                }
                val source = response.body?.source()
                    ?: run {
                        emit(ImportProgress.Failed("Empty response"))
                        return@flow
                    }
                val lines = generateSequence { source.readUtf8Line() }
                emitAll(
                    importer.import(lines, generation) { epgUrl ->
                        settings.setEpgUrl(epgUrl)
                    }
                )
            }
        } catch (e: IOException) {
            runCatching { channelDao.deleteGeneration(generation) }
            emit(ImportProgress.Failed(userMessage(e)))
        } catch (e: IllegalArgumentException) {
            emit(ImportProgress.Failed("Invalid playlist URL"))
        }
    }.flowOn(Dispatchers.IO)

    private fun userMessage(e: IOException): String {
        val isCertError = generateSequence<Throwable>(e) { it.cause }
            .any { it is SSLException || it is CertificateException }
        if (!isCertError) return e.message ?: "Network error"

        val now = Calendar.getInstance()
        // Cert validation is time-based; a box with a dead clock battery
        // resets to the past and fails every certificate on the planet.
        return if (now.get(Calendar.YEAR) < 2026) {
            val date = DateFormat.getDateInstance().format(now.time)
            "Secure connection failed — this device's clock is set to $date. " +
                "Fix the date & time in the box settings."
        } else {
            "Secure connection failed — this device cannot validate the " +
                "server's certificate. As a last resort, enable " +
                "\"Trust all certificates\" in Settings."
        }
    }

    suspend fun channelCount(): Int = channelDao.count()

    suspend fun clearCache() {
        channelDao.clearAll()
        recentDao.clearAll()
        programmeDao.clearAll()
    }
}
