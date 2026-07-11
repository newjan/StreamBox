package com.streambox.app.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.app.data.ImportProgress
import com.streambox.app.player.ZapContext
import com.streambox.app.ui.home.HomeViewModel
import com.streambox.app.ui.shared.ChannelCard
import com.streambox.app.ui.shared.RowHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHomeScreen(
    onPlayChannel: (String, ZapContext) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val specialRows by viewModel.specialRows.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()
    val nowTitles by viewModel.nowTitles.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StreamBox") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenBrowse) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "All channels")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ImportProgressBanner(importProgress)

            if (specialRows.isEmpty() && categories.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (importProgress is ImportProgress.Running || channelCount == 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                text = when (val p = importProgress) {
                                    is ImportProgress.Running -> "Importing channels… ${p.count}"
                                    else -> "Loading channels…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    } else {
                        Text("No channels yet — pull down or check Settings")
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(specialRows, key = { it.title }) { row ->
                        PhoneChannelRow(
                            title = row.title,
                            channels = row.channels,
                            nowTitles = nowTitles,
                            onPlayChannel = onPlayChannel,
                        )
                    }
                    // Category rows load their channels only while visible, so
                    // playlists with 100+ groups all get a row without keeping
                    // 100+ live queries around.
                    items(categories, key = { it }) { category ->
                        val channels by remember(category) { viewModel.channelsFor(category) }
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        if (channels.isNotEmpty()) {
                            PhoneChannelRow(
                                title = category,
                                channels = channels,
                                nowTitles = nowTitles,
                                onPlayChannel = onPlayChannel,
                            )
                        } else {
                            RowHeader(category)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneChannelRow(
    title: String,
    channels: List<com.streambox.app.data.db.ChannelWithState>,
    nowTitles: Map<String, String>,
    onPlayChannel: (String, ZapContext) -> Unit,
) {
    RowHeader(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(channels, key = { it.channel.key }) { channel ->
            ChannelCard(
                channel = channel,
                subtitle = channel.channel.tvgId?.let(nowTitles::get),
                onClick = {
                    onPlayChannel(channel.channel.key, rowZapContext(title))
                },
            )
        }
    }
}

private fun rowZapContext(rowTitle: String): ZapContext = when (rowTitle) {
    HomeViewModel.FAVORITES -> ZapContext(favoritesOnly = true)
    HomeViewModel.CONTINUE_WATCHING -> ZapContext()
    else -> ZapContext(category = rowTitle)
}

@Composable
fun ImportProgressBanner(progress: ImportProgress?) {
    when (progress) {
        is ImportProgress.Running -> Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "Updating playlist… ${progress.count} channels",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        is ImportProgress.Failed -> Text(
            text = "Playlist update failed: ${progress.message}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        else -> Unit
    }
}
