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
import com.streambox.app.player.ZapContext
import com.streambox.app.ui.home.HomeViewModel
import com.streambox.app.ui.home.HomeRow

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
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()

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

        (importProgress as? ImportProgress.Running)?.let { progress ->
            Text(
                text = "Updating playlist… ${progress.count} channels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
            )
        }

        if (rows.isEmpty()) {
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
                items(rows, key = { it.title }) { row ->
                    TvHomeRow(row = row, onPlayChannel = onPlayChannel)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvHomeRow(
    row: HomeRow,
    onPlayChannel: (String, ZapContext) -> Unit,
) {
    Column {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(row.channels, key = { it.channel.key }) { channel ->
                TvChannelCard(
                    channel = channel,
                    onClick = {
                        onPlayChannel(channel.channel.key, rowZapContext(row.title))
                    },
                )
            }
        }
    }
}

private fun rowZapContext(rowTitle: String): ZapContext = when (rowTitle) {
    HomeViewModel.FAVORITES -> ZapContext(favoritesOnly = true)
    HomeViewModel.CONTINUE_WATCHING -> ZapContext()
    else -> ZapContext(category = rowTitle)
}
