package com.streambox.app.data.health

import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelHealthDao
import com.streambox.app.data.db.ChannelHealthEntity
import com.streambox.app.data.db.HealthStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class ScanProgress(
    val running: Boolean = false,
    val checked: Int = 0,
    val total: Int = 0,
    val working: Int = 0,
)

/**
 * Probes stream URLs with a short ranged GET and records OK/DEAD per channel.
 * Runs in an app-scoped coroutine so it survives leaving the Settings screen;
 * one scan at a time, cancellable, bounded concurrency.
 *
 * A probe is a liveness signal, not proof: servers that reject probes but
 * serve real players will be marked dead. The player also updates health
 * passively on every playback attempt, which corrects such cases over time.
 */
@Singleton
class ChannelHealthChecker @Inject constructor(
    okHttpClient: OkHttpClient,
    private val channelDao: ChannelDao,
    private val healthDao: ChannelHealthDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    private val probeClient = okHttpClient.newBuilder()
        .callTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .connectTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    /** Starts a scan unless one is already running. */
    fun start(recheckFresh: Boolean = false) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            val now = System.currentTimeMillis()
            val skip = if (recheckFresh) {
                emptySet()
            } else {
                healthDao.checkedSince(now - FRESH_MS).toHashSet()
            }
            val targets = channelDao.keyUrls().filter { it.key !in skip }
            if (targets.isEmpty()) {
                _progress.value = ScanProgress(running = false)
                return@launch
            }

            val checked = AtomicInteger(0)
            val working = AtomicInteger(0)
            _progress.value = ScanProgress(running = true, total = targets.size)

            val semaphore = Semaphore(CONCURRENCY)
            val jobs = targets.map { target ->
                launch {
                    semaphore.withPermit {
                        val ok = probe(target.url)
                        if (ok) working.incrementAndGet()
                        healthDao.upsert(
                            ChannelHealthEntity(
                                channelKey = target.key,
                                status = if (ok) HealthStatus.OK else HealthStatus.DEAD,
                                checkedAt = System.currentTimeMillis(),
                            )
                        )
                        val done = checked.incrementAndGet()
                        if (done % PROGRESS_EVERY == 0 || done == targets.size) {
                            _progress.value = ScanProgress(
                                running = done < targets.size,
                                checked = done,
                                total = targets.size,
                                working = working.get(),
                            )
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
            _progress.value = _progress.value.copy(running = false)
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        _progress.value = _progress.value.copy(running = false)
    }

    private fun probe(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-2047")
            .build()
        probeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            // Read a small chunk to confirm the stream actually serves bytes.
            response.body?.source()?.request(64) ?: false
        }
    }.getOrDefault(false)

    private companion object {
        const val CONCURRENCY = 24
        const val PROBE_TIMEOUT_S = 8L
        const val PROGRESS_EVERY = 10
        /** Don't re-probe channels checked within the last 12 hours. */
        const val FRESH_MS = 12 * 3_600_000L
    }
}
