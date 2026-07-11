package com.streambox.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.app.data.ImportProgress
import com.streambox.app.data.PlaylistRepository
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelWithState
import com.streambox.app.data.db.FavoriteDao
import com.streambox.app.data.db.ProgrammeDao
import com.streambox.app.data.db.RecentDao
import com.streambox.app.data.epg.EpgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRow(val title: String, val channels: List<ChannelWithState>)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val epgRepository: EpgRepository,
    private val channelDao: ChannelDao,
    favoriteDao: FavoriteDao,
    recentDao: RecentDao,
    programmeDao: ProgrammeDao,
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

    /** Continue Watching + Favorites; small and always loaded eagerly. */
    val specialRows: StateFlow<List<HomeRow>> = combine(
        recentDao.recents(20),
        favoriteDao.favorites(20),
    ) { recents, favorites ->
        buildList {
            if (recents.isNotEmpty()) add(HomeRow(CONTINUE_WATCHING, recents))
            if (favorites.isNotEmpty()) add(HomeRow(FAVORITES, favorites))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Every group-title in the playlist, uncapped. Rows fetch their channels
     * lazily via [channelsFor] only while visible, so a playlist with 100+
     * groups (e.g. grouped-by-country) costs a bounded number of live queries.
     */
    val categories: StateFlow<List<String>> = channelDao.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun channelsFor(category: String): Flow<List<ChannelWithState>> =
        channelDao.channelsForCategory(category, CHANNELS_PER_ROW)

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
