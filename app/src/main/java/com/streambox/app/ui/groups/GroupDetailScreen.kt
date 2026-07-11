package com.streambox.app.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.data.settings.ViewMode
import com.streambox.app.player.ZapContext
import com.streambox.app.ui.shared.ChannelCard
import com.streambox.app.ui.shared.ChannelListItem
import com.streambox.app.ui.shared.countryFlagEmoji

/**
 * All channels of one category/country, paged, in grid or list form.
 * Unlike the Home rows (top 25), this shows the complete group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onPlayChannel: (String, ZapContext) -> Unit,
    onBack: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    val title = if (viewModel.groupType == HomeGroupBy.COUNTRY) {
        "${countryFlagEmoji(viewModel.groupKey)} ${viewModel.groupKey}".trim()
    } else {
        viewModel.groupKey
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.setViewMode(
                                if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                            )
                        },
                    ) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.GRID) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = if (viewMode == ViewMode.GRID) {
                                "Switch to list"
                            } else {
                                "Switch to grid"
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (viewMode) {
            ViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(
                    count = channels.itemCount,
                    key = channels.itemKey { it.channel.key },
                ) { index ->
                    channels[index]?.let { channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = {
                                onPlayChannel(channel.channel.key, viewModel.zapContext())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            ViewMode.LIST -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(
                    count = channels.itemCount,
                    key = channels.itemKey { it.channel.key },
                ) { index ->
                    channels[index]?.let { channel ->
                        ChannelListItem(
                            channel = channel,
                            onClick = {
                                onPlayChannel(channel.channel.key, viewModel.zapContext())
                            },
                        )
                    }
                }
            }
        }
    }
}
