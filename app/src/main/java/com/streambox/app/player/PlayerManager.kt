package com.streambox.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                Player.STATE_READY -> _state.value = PlayerUiState.Playing
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = PlayerUiState.Error(
                "Stream unavailable — it may be offline or geo-blocked"
            )
        }
    }

    init {
        player.addListener(listener)
    }

    fun play(url: String) {
        currentUrl = url
        _state.value = PlayerUiState.Buffering
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    fun retry() {
        currentUrl?.let(::play)
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}
