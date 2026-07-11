package com.streambox.app.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelHealthDao
import com.streambox.app.data.db.ChannelHealthEntity
import com.streambox.app.data.db.ChannelWithState
import com.streambox.app.data.db.FavoriteDao
import com.streambox.app.data.db.GroupCount
import com.streambox.app.data.db.HealthStatus
import com.streambox.app.data.db.ProgrammeDao
import com.streambox.app.data.db.RecentDao
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.data.settings.SettingsRepository
import com.streambox.app.player.PlayerManager
import com.streambox.app.player.PlayerUiState
import com.streambox.app.player.ZapContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val initialZap = ZapContext.decode(savedStateHandle.get<String>("ctx"))
    private val initialKey: String = checkNotNull(savedStateHandle["channelKey"])

    /**
     * The active zapping filter. Starts as the context playback was launched
     * from; picking a channel from another group in the panel retargets it so
     * up/down zapping follows the new group.
     */
    private val zapCtx = MutableStateFlow(initialZap)

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

    // ---- In-player channel panel: two-level (groups → channels) browser ----

    /** Whether the panel's left column lists categories or countries. */
    val panelGroupType = MutableStateFlow(
        if (initialZap.country != null) HomeGroupBy.COUNTRY else HomeGroupBy.CATEGORY,
    )

    /**
     * Selected group in the panel; null means "All channels" and
     * [FAVORITES_GROUP] is the pinned Favorites pseudo-group.
     */
    val panelSelectedGroup = MutableStateFlow<String?>(
        when {
            initialZap.favoritesOnly -> FAVORITES_GROUP
            else -> initialZap.category ?: initialZap.country
        },
    )

    val favoritesCount: StateFlow<Int> = favoriteDao.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // Without an explicit group from the launch context, restore the
        // user's last panel grouping choice.
        if (initialZap.category == null && initialZap.country == null) {
            viewModelScope.launch {
                panelGroupType.value = settings.panelGroupBy.first()
            }
        }
    }

    val panelGroups: StateFlow<List<GroupCount>> = panelGroupType
        .flatMapLatest { type ->
            when (type) {
                HomeGroupBy.CATEGORY -> channelDao.categoryCounts()
                HomeGroupBy.COUNTRY -> channelDao.countryCounts()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Channels of the panel's selected group, paged. */
    val listChannels: Flow<PagingData<ChannelWithState>> =
        combine(panelGroupType, panelSelectedGroup, settings.hideDead, ::Triple)
            .flatMapLatest { (type, group, hideDead) ->
                Pager(
                    config = PagingConfig(
                        pageSize = 60,
                        prefetchDistance = 120,
                        enablePlaceholders = false,
                        maxSize = 600,
                    ),
                ) {
                    val realGroup = group.takeIf { it != FAVORITES_GROUP }
                    channelDao.pagingSource(
                        query = "",
                        category = realGroup.takeIf { type == HomeGroupBy.CATEGORY },
                        country = realGroup.takeIf { type == HomeGroupBy.COUNTRY },
                        favoritesOnly = group == FAVORITES_GROUP,
                        hideDead = hideDead,
                    )
                }.flow
            }
            .cachedIn(viewModelScope)

    fun setPanelGroupType(type: HomeGroupBy) {
        if (panelGroupType.value != type) {
            panelGroupType.value = type
            // Favorites is grouping-independent; anything else resets to All.
            if (panelSelectedGroup.value != FAVORITES_GROUP) {
                panelSelectedGroup.value = null
            }
            viewModelScope.launch { settings.setPanelGroupBy(type) }
        }
    }

    fun selectPanelGroup(group: String?) {
        panelSelectedGroup.value = group
    }

    /**
     * Direct selection from the channel panel. Also retargets the zapping
     * filter to the panel's group so up/down continues within it.
     */
    fun playByKey(key: String) {
        val group = panelSelectedGroup.value
        val realGroup = group.takeIf { it != FAVORITES_GROUP }
        zapCtx.value = ZapContext(
            category = realGroup.takeIf { panelGroupType.value == HomeGroupBy.CATEGORY },
            country = realGroup.takeIf { panelGroupType.value == HomeGroupBy.COUNTRY },
            favoritesOnly = group == FAVORITES_GROUP,
        )
        if (key == _currentChannel.value?.channel?.key) return
        viewModelScope.launch {
            channelDao.byKeyOnce(key)?.let(::switchTo)
        }
    }

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
            val (q, cat, co, fav) = zapCtx.value
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

    companion object {
        /** Sentinel key for the pinned Favorites entry in the panel. */
        const val FAVORITES_GROUP = " favorites"
    }
}

enum class ResizeMode { FIT, FILL, ZOOM }
