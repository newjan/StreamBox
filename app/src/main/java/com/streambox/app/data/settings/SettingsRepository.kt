package com.streambox.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/index.m3u"

data class PlaylistPreset(val name: String, val url: String)

object PlaylistPresets {
    val presets = listOf(
        PlaylistPreset("iptv-org: All channels", DEFAULT_PLAYLIST_URL),
        PlaylistPreset("iptv-org: Grouped by category", "https://iptv-org.github.io/iptv/index.category.m3u"),
        PlaylistPreset("iptv-org: Grouped by country", "https://iptv-org.github.io/iptv/index.country.m3u"),
        PlaylistPreset("iptv-org: Grouped by language", "https://iptv-org.github.io/iptv/index.language.m3u"),
    )
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val THEME_DARK = booleanPreferencesKey("theme_dark")
        val EPG_ENABLED = booleanPreferencesKey("epg_enabled")
        val EPG_URL = stringPreferencesKey("epg_url")
    }

    val playlistUrl: Flow<String> =
        dataStore.data.map { it[Keys.PLAYLIST_URL] ?: DEFAULT_PLAYLIST_URL }

    val themeDark: Flow<Boolean> =
        dataStore.data.map { it[Keys.THEME_DARK] ?: true }

    val epgEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.EPG_ENABLED] ?: true }

    val epgUrl: Flow<String?> =
        dataStore.data.map { it[Keys.EPG_URL]?.takeIf(String::isNotBlank) }

    suspend fun setPlaylistUrl(url: String) =
        dataStore.edit { it[Keys.PLAYLIST_URL] = url.trim() }

    suspend fun setThemeDark(dark: Boolean) =
        dataStore.edit { it[Keys.THEME_DARK] = dark }

    suspend fun setEpgEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.EPG_ENABLED] = enabled }

    suspend fun setEpgUrl(url: String?) =
        dataStore.edit { it[Keys.EPG_URL] = url.orEmpty() }
}
