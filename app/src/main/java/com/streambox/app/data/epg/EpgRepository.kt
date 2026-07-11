package com.streambox.app.data.epg

import com.streambox.app.data.db.ProgrammeDao
import com.streambox.app.data.db.ProgrammeEntity
import com.streambox.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional, lazy "now playing" layer. Runs only after a successful channel
 * import, only when enabled and the playlist advertised a guide URL, and
 * never surfaces failures — the channel list must never depend on it.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val programmeDao: ProgrammeDao,
    private val settings: SettingsRepository,
    private val parser: XmltvParser,
) {
    suspend fun refreshIfEnabled() = withContext(Dispatchers.IO) {
        runCatching {
            if (!settings.epgEnabled.first()) return@withContext
            val url = settings.epgUrl.first() ?: return@withContext

            val now = System.currentTimeMillis()
            val windowStart = now - 6 * HOUR_MS
            val windowEnd = now + 12 * HOUR_MS

            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val body = response.body ?: return@withContext
                val stream = if (url.endsWith(".gz")) {
                    GZIPInputStream(body.byteStream())
                } else {
                    body.byteStream()
                }
                // The 18h window keeps this list small even for national guides.
                val programmes = ArrayList<ProgrammeEntity>(4096)
                parser.parse(stream, windowStart, windowEnd) { programmes += it }
                programmes.chunked(BATCH_SIZE).forEach { programmeDao.insertAll(it) }
                programmeDao.pruneEndedBefore(now)
            }
        }
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val BATCH_SIZE = 500
    }
}
