package com.streambox.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.app.data.ImportProgress
import com.streambox.app.data.PlaylistRepository
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelWithState
import com.streambox.app.data.db.CustomCategoryDao
import com.streambox.app.data.db.FavoriteDao
import com.streambox.app.data.db.ProgrammeDao
import com.streambox.app.data.db.RecentDao
import com.streambox.app.data.epg.EpgRepository
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.data.settings.SettingsRepository
import com.streambox.app.player.ZapContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRow(
    val title: String,
    val channels: List<ChannelWithState>,
    /** Zapping filter for playback launched from this row. */
    val zap: ZapContext = ZapContext(),
    /** Stable list key (custom lists could collide with built-in titles). */
    val key: String = title,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val epgRepository: EpgRepository,
    private val channelDao: ChannelDao,
    private val settings: SettingsRepository,
    favoriteDao: FavoriteDao,
    recentDao: RecentDao,
    programmeDao: ProgrammeDao,
    customCategoryDao: CustomCategoryDao,
) : ViewModel() {

    /** tvg-id → currently airing title, for card subtitles. Empty without EPG. */
    val nowTitles: StateFlow<Map<String, String>> =
        programmeDao.nowTitles(System.currentTimeMillis())
            .map { titles -> titles.associate { it.tvgId to it.title } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    val channelCount: StateFlow<Int> = channelDao.countFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Eagerly shared so [zapContextFor] can read current values synchronously. */
    val groupBy: StateFlow<HomeGroupBy> = settings.homeGroupBy
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeGroupBy.CATEGORY)

    val favoritesOnly: StateFlow<Boolean> = settings.homeFavoritesOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val hideDead: StateFlow<Boolean> = settings.hideDead
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** User-created lists as rows, each limited like the group rows. */
    private val customRows: Flow<List<HomeRow>> =
        customCategoryDao.categoriesWithCounts().flatMapLatest { lists ->
            if (lists.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    lists.map { list ->
                        customCategoryDao.channelsFor(list.id, CHANNELS_PER_ROW).map { channels ->
                            HomeRow(
                                title = list.name,
                                channels = channels,
                                zap = ZapContext(customCategoryId = list.id),
                                key = "custom:${list.id}",
                            )
                        }
                    }
                ) { rows -> rows.filter { it.channels.isNotEmpty() } }
            }
        }

    /** Continue Watching + Favorites + custom lists; always loaded eagerly. */
    val specialRows: StateFlow<List<HomeRow>> = combine(
        recentDao.recents(20),
        favoriteDao.favorites(20),
        customRows,
    ) { recents, favorites, customs ->
        buildList {
            if (recents.isNotEmpty()) add(HomeRow(CONTINUE_WATCHING, recents))
            if (favorites.isNotEmpty()) {
                add(HomeRow(FAVORITES, favorites, ZapContext(favoritesOnly = true)))
            }
            addAll(customs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Row keys for the selected grouping (all group-titles or all countries),
     * uncapped. Rows fetch their channels lazily via [channelsFor] only while
     * visible, keeping live queries bounded on 100+ group playlists.
     */
    val rowKeys: StateFlow<List<String>> = groupBy
        .flatMapLatest { grouping ->
            when (grouping) {
                HomeGroupBy.CATEGORY -> channelDao.categories()
                HomeGroupBy.COUNTRY -> channelDao.countries()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun channelsFor(rowKey: String): Flow<List<ChannelWithState>> =
        combine(groupBy, favoritesOnly, hideDead, ::Triple)
            .flatMapLatest { (grouping, favOnly, hide) ->
                when (grouping) {
                    HomeGroupBy.CATEGORY ->
                        channelDao.channelsForCategory(rowKey, favOnly, hide, CHANNELS_PER_ROW)
                    HomeGroupBy.COUNTRY ->
                        channelDao.channelsForCountry(rowKey, favOnly, hide, CHANNELS_PER_ROW)
                }
            }

    /** Zapping in the player follows the row the channel was launched from. */
    fun zapContextFor(rowTitle: String): ZapContext = when (rowTitle) {
        FAVORITES -> ZapContext(favoritesOnly = true)
        CONTINUE_WATCHING -> ZapContext()
        else -> when (groupBy.value) {
            HomeGroupBy.CATEGORY ->
                ZapContext(category = rowTitle, favoritesOnly = favoritesOnly.value)
            HomeGroupBy.COUNTRY ->
                ZapContext(country = rowTitle, favoritesOnly = favoritesOnly.value)
        }
    }

    fun setGroupBy(value: HomeGroupBy) {
        viewModelScope.launch { settings.setHomeGroupBy(value) }
    }

    fun setFavoritesOnly(value: Boolean) {
        viewModelScope.launch { settings.setHomeFavoritesOnly(value) }
    }

    init {
        refresh()
    }

    /** Cached channels show instantly; the playlist refreshes in the background. */
    fun refresh() {
        viewModelScope.launch {
            if (_importProgress.value is ImportProgress.Running) return@launch
            playlistRepository.refresh().collect { progress ->
                _importProgress.value = progress
                if (progress is ImportProgress.Done) {
                    // Optional now-playing guide; failures are silent by design.
                    epgRepository.refreshIfEnabled()
                }
            }
        }
    }

    fun dismissProgress() {
        _importProgress.value = null
    }

    companion object {
        const val CONTINUE_WATCHING = "Continue Watching"
        const val FAVORITES = "Favorites"
        private const val CHANNELS_PER_ROW = 25
    }
}
