package com.streambox.app.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelHealthDao
import com.streambox.app.data.db.ChannelHealthEntity
import com.streambox.app.data.db.ChannelWithState
import com.streambox.app.data.db.FavoriteDao
import com.streambox.app.data.db.HealthStatus
import com.streambox.app.data.db.ProgrammeDao
import com.streambox.app.data.db.RecentDao
import com.streambox.app.data.settings.SettingsRepository
import com.streambox.app.player.PlayerManager
import com.streambox.app.player.PlayerUiState
import com.streambox.app.player.ZapContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val recentDao: RecentDao,
    private val healthDao: ChannelHealthDao,
    private val settings: SettingsRepository,
    programmeDao: ProgrammeDao,
) : ViewModel() {

    private val zapContext = ZapContext.decode(savedStateHandle.get<String>("ctx"))
    private val initialKey: String = checkNotNull(savedStateHandle["channelKey"])

    val playerManager = PlayerManager(context, okHttpClient)
    val playerState: StateFlow<PlayerUiState> = playerManager.state

    private val _currentChannel = MutableStateFlow<ChannelWithState?>(null)
    val currentChannel: StateFlow<ChannelWithState?> = _currentChannel.asStateFlow()

    val isFavorite: StateFlow<Boolean> = _currentChannel
        .flatMapLatest { ch ->
            ch?.let { favoriteDao.isFavorite(it.channel.key) } ?: flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val nowPlaying: StateFlow<String?> = _currentChannel
        .flatMapLatest { ch ->
            val tvgId = ch?.channel?.tvgId
            if (tvgId == null) flowOf(null)
            else programmeDao.nowPlaying(tvgId, System.currentTimeMillis())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Bumped every time the channel changes so the UI can re-show the banner. */
    private val _channelChangeTick = MutableStateFlow(0)
    val channelChangeTick: StateFlow<Int> = _channelChangeTick.asStateFlow()

    /** Cycles fit → fill → zoom, mirrored to PlayerView's resize mode. */
    private val _resizeMode = MutableStateFlow(ResizeMode.FIT)
    val resizeMode: StateFlow<ResizeMode> = _resizeMode.asStateFlow()

    init {
        viewModelScope.launch {
            channelDao.byKeyOnce(initialKey)?.let(::switchTo)
        }
        // Every playback attempt doubles as a health check: success marks the
        // channel OK, a player error marks it dead.
        viewModelScope.launch {
            playerManager.state.collect { state ->
                val key = _currentChannel.value?.channel?.key ?: return@collect
                when (state) {
                    is PlayerUiState.Playing -> recordHealth(key, HealthStatus.OK)
                    is PlayerUiState.Error -> recordHealth(key, HealthStatus.DEAD)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun recordHealth(key: String, status: Int) {
        runCatching {
            healthDao.upsert(ChannelHealthEntity(key, status, System.currentTimeMillis()))
        }
    }

    fun zapNext() = zap(forward = true)
    fun zapPrev() = zap(forward = false)

    private fun zap(forward: Boolean) {
        val current = _currentChannel.value ?: return
        viewModelScope.launch {
            val (q, cat, co, fav) = zapContext
            val hide = settings.hideDead.first()
            val next = if (forward) {
                channelDao.nextAfter(current.channel.name, current.channel.key, q, cat, co, fav, hide)
                    ?: channelDao.first(q, cat, co, fav, hide)
            } else {
                channelDao.prevBefore(current.channel.name, current.channel.key, q, cat, co, fav, hide)
                    ?: channelDao.last(q, cat, co, fav, hide)
            }
            if (next != null && next.channel.key != current.channel.key) {
                switchTo(next)
            }
        }
    }

    fun retry() = playerManager.retry()

    fun toggleFavorite() {
        val key = _currentChannel.value?.channel?.key ?: return
        viewModelScope.launch { favoriteDao.toggle(key, System.currentTimeMillis()) }
    }

    fun cycleResizeMode() {
        _resizeMode.value = when (_resizeMode.value) {
            ResizeMode.FIT -> ResizeMode.FILL
            ResizeMode.FILL -> ResizeMode.ZOOM
            ResizeMode.ZOOM -> ResizeMode.FIT
        }
    }

    private fun switchTo(channel: ChannelWithState) {
        _currentChannel.value = channel
        _channelChangeTick.value++
        playerManager.play(channel.channel.url)
        viewModelScope.launch {
            recentDao.touch(channel.channel.key, System.currentTimeMillis())
        }
    }

    override fun onCleared() {
        playerManager.release()
    }
}

enum class ResizeMode { FIT, FILL, ZOOM }
