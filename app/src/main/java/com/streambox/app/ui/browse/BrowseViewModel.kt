package com.streambox.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelWithState
import com.streambox.app.data.settings.SettingsRepository
import com.streambox.app.player.ZapContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs both the All Channels screen and Search: a paged, filtered,
 * name-ordered query over the 12k+ cached channels.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    channelDao: ChannelDao,
    settings: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    private val _country = MutableStateFlow<String?>(null)
    val country: StateFlow<String?> = _country.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val hideDead: StateFlow<Boolean> = settings.hideDead
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val categories: StateFlow<List<String>> = channelDao.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val countries: StateFlow<List<String>> = channelDao.countries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class Filter(
        val query: String,
        val category: String?,
        val country: String?,
        val favoritesOnly: Boolean,
        val hideDead: Boolean,
    )

    val channels: Flow<PagingData<ChannelWithState>> =
        combine(
            _query.debounce(200),
            _category,
            _country,
            _favoritesOnly,
            hideDead,
        ) { query, category, country, favoritesOnly, hide ->
            Filter(query.trim(), category, country, favoritesOnly, hide)
        }
            .flatMapLatest { filter ->
                Pager(
                    config = PagingConfig(
                        pageSize = 60,
                        prefetchDistance = 120,
                        enablePlaceholders = false,
                        maxSize = 600,
                    ),
                ) {
                    channelDao.pagingSource(
                        filter.query,
                        filter.category,
                        filter.country,
                        filter.favoritesOnly,
                        filter.hideDead,
                    )
                }.flow
            }
            .cachedIn(viewModelScope)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setCategory(value: String?) {
        _category.value = value
    }

    fun setCountry(value: String?) {
        _country.value = value
    }

    fun setFavoritesOnly(value: Boolean) {
        _favoritesOnly.value = value
    }

    /** The player zaps within whatever filter the user launched playback from. */
    fun zapContext(): ZapContext = ZapContext(
        query = _query.value.trim(),
        category = _category.value,
        country = _country.value,
        favoritesOnly = _favoritesOnly.value,
    )
}
