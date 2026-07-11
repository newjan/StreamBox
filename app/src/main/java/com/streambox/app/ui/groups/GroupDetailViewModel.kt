package com.streambox.app.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelWithState
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.data.settings.SettingsRepository
import com.streambox.app.data.settings.ViewMode
import com.streambox.app.player.ZapContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Full, paged channel list of one category or country. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    channelDao: ChannelDao,
    private val settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupType: HomeGroupBy =
        runCatching { HomeGroupBy.valueOf(savedStateHandle.get<String>("type").orEmpty()) }
            .getOrDefault(HomeGroupBy.CATEGORY)

    val groupKey: String = checkNotNull(savedStateHandle["key"])

    val viewMode: StateFlow<ViewMode> = settings.groupViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewMode.GRID)

    val channels: Flow<PagingData<ChannelWithState>> = settings.hideDead
        .flatMapLatest { hideDead ->
            Pager(
                config = PagingConfig(
                    pageSize = 60,
                    prefetchDistance = 120,
                    enablePlaceholders = false,
                    maxSize = 900,
                ),
            ) {
                channelDao.pagingSource(
                    query = "",
                    category = groupKey.takeIf { groupType == HomeGroupBy.CATEGORY },
                    country = groupKey.takeIf { groupType == HomeGroupBy.COUNTRY },
                    favoritesOnly = false,
                    hideDead = hideDead,
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { settings.setGroupViewMode(mode) }
    }

    fun zapContext(): ZapContext = when (groupType) {
        HomeGroupBy.CATEGORY -> ZapContext(category = groupKey)
        HomeGroupBy.COUNTRY -> ZapContext(country = groupKey)
    }
}
