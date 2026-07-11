package com.streambox.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streambox.app.data.ImportProgress
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.player.ZapContext
import com.streambox.app.ui.home.HomeViewModel
import com.streambox.app.ui.shared.countryFlagEmoji

/**
 * Netflix-style rows: nav pills on top, then Continue Watching, Favorites,
 * and one row per category. Fully D-pad driven; row focus is restored when
 * returning from the player.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvHomeScreen(
    onPlayChannel: (String, ZapContext) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val specialRows by viewModel.specialRows.collectAsStateWithLifecycle()
    val rowKeys by viewModel.rowKeys.collectAsStateWithLifecycle()
    val groupBy by viewModel.groupBy.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()
    val nowTitles by viewModel.nowTitles.collectAsStateWithLifecycle()

    val firstNavFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstNavFocus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Text(
                text = "StreamBox",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.weight(1f))
            TvPill(
                label = "All Channels",
                onClick = onOpenBrowse,
                modifier = Modifier.focusRequester(firstNavFocus),
            )
            TvPill(label = "Search", onClick = onOpenSearch)
            TvPill(label = "Settings", onClick = onOpenSettings)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        ) {
            TvPill(
                label = "By category",
                selected = groupBy == HomeGroupBy.CATEGORY,
                onClick = { viewModel.setGroupBy(HomeGroupBy.CATEGORY) },
            )
            TvPill(
                label = "By country",
                selected = groupBy == HomeGroupBy.COUNTRY,
                onClick = { viewModel.setGroupBy(HomeGroupBy.COUNTRY) },
            )
            TvPill(
                label = "♥ Favorites",
                selected = favoritesOnly,
                onClick = { viewModel.setFavoritesOnly(!favoritesOnly) },
            )
        }

        (importProgress as? ImportProgress.Running)?.let { progress ->
            Text(
                text = "Updating playlist… ${progress.count} channels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
            )
        }

        if (specialRows.isEmpty() && rowKeys.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = when (val p = importProgress) {
                            is ImportProgress.Running -> "Importing channels… ${p.count}"
                            is ImportProgress.Failed ->
                                "Playlist update failed: ${p.message}"
                            else -> if (channelCount == 0) "Loading channels…" else ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(specialRows, key = { it.title }) { row ->
                    TvHomeRow(
                        title = row.title,
                        channels = row.channels,
                        nowTitles = nowTitles,
                        zapContext = { viewModel.zapContextFor(row.title) },
                        onPlayChannel = onPlayChannel,
                    )
                }
                // Group rows query their channels only while on screen so
                // grouped-by-country playlists (100+ rows) all appear.
                items(rowKeys, key = { it }) { rowKey ->
                    val channels by remember(rowKey, groupBy, favoritesOnly) {
                        viewModel.channelsFor(rowKey)
                    }.collectAsStateWithLifecycle(initialValue = emptyList())
                    val title = if (groupBy == HomeGroupBy.COUNTRY) {
                        "${countryFlagEmoji(rowKey)} $rowKey".trim()
                    } else {
                        rowKey
                    }
                    if (channels.isNotEmpty()) {
                        TvHomeRow(
                            title = title,
                            channels = channels,
                            nowTitles = nowTitles,
                            zapContext = { viewModel.zapContextFor(rowKey) },
                            onPlayChannel = onPlayChannel,
                        )
                    } else if (!favoritesOnly) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvHomeRow(
    title: String,
    channels: List<com.streambox.app.data.db.ChannelWithState>,
    nowTitles: Map<String, String>,
    zapContext: () -> ZapContext,
    onPlayChannel: (String, ZapContext) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(channels, key = { it.channel.key }) { channel ->
                TvChannelCard(
                    channel = channel,
                    subtitle = channel.channel.tvgId?.let(nowTitles::get),
                    onClick = {
                        onPlayChannel(channel.channel.key, zapContext())
                    },
                )
            }
        }
    }
}
