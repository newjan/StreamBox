package com.streambox.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.GroupCount
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.data.settings.SettingsRepository
import com.streambox.app.data.settings.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Groups browser: all categories or countries with counts. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    channelDao: ChannelDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _groupType = MutableStateFlow(HomeGroupBy.CATEGORY)
    val groupType: StateFlow<HomeGroupBy> = _groupType.asStateFlow()

    val viewMode: StateFlow<ViewMode> = settings.groupViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewMode.GRID)

    val groups: StateFlow<List<GroupCount>> = _groupType
        .flatMapLatest { type ->
            when (type) {
                HomeGroupBy.CATEGORY -> channelDao.categoryCounts()
                HomeGroupBy.COUNTRY -> channelDao.countryCounts()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setGroupType(type: HomeGroupBy) {
        _groupType.value = type
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { settings.setGroupViewMode(mode) }
    }
}
