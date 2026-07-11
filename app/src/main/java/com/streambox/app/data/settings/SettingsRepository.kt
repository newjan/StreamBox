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

enum class HomeGroupBy { CATEGORY, COUNTRY }

enum class ViewMode { GRID, LIST }

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
        val HOME_GROUP_BY = stringPreferencesKey("home_group_by")
        val HOME_FAVORITES_ONLY = booleanPreferencesKey("home_favorites_only")
        val HIDE_DEAD = booleanPreferencesKey("hide_dead")
        val GROUP_VIEW_MODE = stringPreferencesKey("group_view_mode")
        val TRUST_ALL_CERTS = booleanPreferencesKey("trust_all_certs")
    }

    val playlistUrl: Flow<String> =
        dataStore.data.map { it[Keys.PLAYLIST_URL] ?: DEFAULT_PLAYLIST_URL }

    val themeDark: Flow<Boolean> =
        dataStore.data.map { it[Keys.THEME_DARK] ?: true }

    val epgEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.EPG_ENABLED] ?: true }

    val epgUrl: Flow<String?> =
        dataStore.data.map { it[Keys.EPG_URL]?.takeIf(String::isNotBlank) }

    /** How the Home screen groups its rows. */
    val homeGroupBy: Flow<HomeGroupBy> = dataStore.data.map {
        runCatching { HomeGroupBy.valueOf(it[Keys.HOME_GROUP_BY] ?: "") }
            .getOrDefault(HomeGroupBy.CATEGORY)
    }

    /** When true, Home rows only contain favorited channels. */
    val homeFavoritesOnly: Flow<Boolean> =
        dataStore.data.map { it[Keys.HOME_FAVORITES_ONLY] ?: false }

    /** When true, channels last seen as dead are hidden from the main lists. */
    val hideDead: Flow<Boolean> =
        dataStore.data.map { it[Keys.HIDE_DEAD] ?: false }

    suspend fun setHomeGroupBy(value: HomeGroupBy) =
        dataStore.edit { it[Keys.HOME_GROUP_BY] = value.name }

    suspend fun setHomeFavoritesOnly(value: Boolean) =
        dataStore.edit { it[Keys.HOME_FAVORITES_ONLY] = value }

    suspend fun setHideDead(value: Boolean) =
        dataStore.edit { it[Keys.HIDE_DEAD] = value }

    /** Grid vs list presentation in the Groups browser. */
    val groupViewMode: Flow<ViewMode> = dataStore.data.map {
        runCatching { ViewMode.valueOf(it[Keys.GROUP_VIEW_MODE] ?: "") }
            .getOrDefault(ViewMode.GRID)
    }

    suspend fun setGroupViewMode(value: ViewMode) =
        dataStore.edit { it[Keys.GROUP_VIEW_MODE] = value.name }

    /**
     * Last-resort escape hatch for old boxes whose TLS can't validate modern
     * chains even against the bundled roots. Disables certificate checks.
     */
    val trustAllCerts: Flow<Boolean> =
        dataStore.data.map { it[Keys.TRUST_ALL_CERTS] ?: false }

    suspend fun setTrustAllCerts(value: Boolean) =
        dataStore.edit { it[Keys.TRUST_ALL_CERTS] = value }

    suspend fun setPlaylistUrl(url: String) =
        dataStore.edit { it[Keys.PLAYLIST_URL] = url.trim() }

    suspend fun setThemeDark(dark: Boolean) =
        dataStore.edit { it[Keys.THEME_DARK] = dark }

    suspend fun setEpgEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.EPG_ENABLED] = enabled }

    suspend fun setEpgUrl(url: String?) =
        dataStore.edit { it[Keys.EPG_URL] = url.orEmpty() }
}
