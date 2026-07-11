package com.streambox.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed interface PlayerUiState {
    data object Idle : PlayerUiState
    data object Buffering : PlayerUiState
    data object Playing : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

/**
 * Owns the ExoPlayer instance. Streams go through OkHttp (browser UA, 15s
 * timeouts, cross-protocol redirects — configured on the injected client).
 * Dead streams surface as [PlayerUiState.Error]; nothing throws.
 */
@androidx.media3.common.util.UnstableApi
class PlayerManager(context: Context, okHttpClient: OkHttpClient) {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var currentUrl: String? = null

    /** Configurable via Settings; updated by the ViewModel. */
    @Volatile
    var retryWindowMs: Long = DEFAULT_RETRY_WINDOW_MS

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var retryJob: Job? = null
    private var firstErrorAt: Long? = null

    val player: ExoPlayer = run {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.value = PlayerUiState.Buffering
                Player.STATE_READY -> {
                    // Stream recovered (or started) — a future error opens a
                    // fresh retry window.
                    firstErrorAt = null
                    _state.value = PlayerUiState.Playing
                }
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Flaky IPTV servers often come back within seconds: keep
            // silently re-preparing for RETRY_WINDOW_MS before giving up.
            val now = System.currentTimeMillis()
            val windowStart = firstErrorAt ?: now.also { firstErrorAt = it }
            if (now - windowStart < retryWindowMs && currentUrl != null) {
                _state.value = PlayerUiState.Buffering
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(RETRY_DELAY_MS)
                    currentUrl?.let { url ->
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
            } else {
                _state.value = PlayerUiState.Error(
                    "Stream unavailable — it may be offline or geo-blocked"
                )
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun play(url: String) {
        retryJob?.cancel()
        firstErrorAt = null
        currentUrl = url
        _state.value = PlayerUiState.Buffering
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    /** Manual retry from the error UI opens a fresh 30s retry window. */
    fun retry() {
        currentUrl?.let(::play)
    }

    fun release() {
        retryJob?.cancel()
        scope.cancel()
        player.removeListener(listener)
        player.release()
    }

    private companion object {
        /** Keep auto-retrying a failed stream this long before surfacing the error. */
        const val DEFAULT_RETRY_WINDOW_MS = 10_000L
        /** Pause between automatic re-prepare attempts. */
        const val RETRY_DELAY_MS = 3_000L
    }
}
