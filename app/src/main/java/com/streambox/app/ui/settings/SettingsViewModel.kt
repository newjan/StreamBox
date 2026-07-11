package com.streambox.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.app.data.ImportProgress
import com.streambox.app.data.PlaylistRepository
import com.streambox.app.data.db.ChannelHealthDao
import com.streambox.app.data.health.ChannelHealthChecker
import com.streambox.app.data.health.ScanProgress
import com.streambox.app.data.settings.DEFAULT_PLAYLIST_URL
import com.streambox.app.data.settings.PlaylistPreset
import com.streambox.app.data.settings.PlaylistPresets
import com.streambox.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
    private val healthChecker: ChannelHealthChecker,
    private val healthDao: ChannelHealthDao,
) : ViewModel() {

    val presets: List<PlaylistPreset> = PlaylistPresets.presets

    val playlistUrl: StateFlow<String> = settings.playlistUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_PLAYLIST_URL)

    val themeDark: StateFlow<Boolean> = settings.themeDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val epgEnabled: StateFlow<Boolean> = settings.epgEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hideDead: StateFlow<Boolean> = settings.hideDead
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Live progress of the stream health scan (app-scoped, survives leaving). */
    val scanProgress: StateFlow<ScanProgress> = healthChecker.progress

    val workingCount: StateFlow<Int> = healthDao.workingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setHideDead(value: Boolean) {
        viewModelScope.launch { settings.setHideDead(value) }
    }

    fun startScan() = healthChecker.start()

    fun stopScan() = healthChecker.stop()

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    /** Saving a new URL kicks off a background re-import immediately. */
    fun savePlaylistUrl(url: String) {
        viewModelScope.launch {
            settings.setPlaylistUrl(url)
            refreshNow()
        }
    }

    fun setThemeDark(dark: Boolean) {
        viewModelScope.launch { settings.setThemeDark(dark) }
    }

    fun setEpgEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setEpgEnabled(enabled) }
    }

    fun refreshNow() {
        viewModelScope.launch {
            playlistRepository.refresh().collect { _importProgress.value = it }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            healthChecker.stop()
            playlistRepository.clearCache()
            healthDao.clearAll()
            _importProgress.value = null
        }
    }
}
