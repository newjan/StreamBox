package com.streambox.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streambox.app.player.ZapContext
import com.streambox.app.ui.browse.BrowseViewModel

private val KEY_ROWS = listOf(
    "1234567890".toList(),
    "QWERTYUIOP".toList(),
    "ASDFGHJKL".toList(),
    "ZXCVBNM".toList(),
)

/**
 * TV search with a D-pad friendly on-screen keyboard. Results update
 * instantly from the local channel cache as keys are pressed.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchScreen(
    onPlayChannel: (String, ZapContext) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    val firstKeyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstKeyFocus.requestFocus() }

    Row(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        // Keyboard panel
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .width(420.dp)
                .padding(start = 48.dp, end = 24.dp),
        ) {
            Text(
                text = if (query.isEmpty()) "Search channels" else query,
                style = MaterialTheme.typography.headlineSmall,
                color = if (query.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            KEY_ROWS.forEachIndexed { rowIndex, keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEachIndexed { keyIndex, key ->
                        TvPill(
                            label = key.toString(),
                            onClick = { viewModel.setQuery(query + key) },
                            modifier = if (rowIndex == 0 && keyIndex == 0) {
                                Modifier.focusRequester(firstKeyFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TvPill(label = "Space", onClick = { viewModel.setQuery("$query ") })
                TvPill(
                    label = "⌫",
                    onClick = { viewModel.setQuery(query.dropLast(1)) },
                )
                TvPill(label = "Clear", onClick = { viewModel.setQuery("") })
            }
        }

        // Results grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 48.dp, top = 8.dp, bottom = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer(),
        ) {
            items(
                count = channels.itemCount,
                key = channels.itemKey { it.channel.key },
            ) { index ->
                channels[index]?.let { channel ->
                    TvChannelCard(
                        channel = channel,
                        onClick = {
                            onPlayChannel(channel.channel.key, viewModel.zapContext())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
