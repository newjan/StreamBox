package com.streambox.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streambox.app.player.ZapContext
import com.streambox.app.ui.browse.BrowseViewModel
import com.streambox.app.ui.shared.countryFlagEmoji

/**
 * All Channels on TV: category and country filter rows on top, then a
 * paged grid of channel cards. Everything reachable by D-pad only.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvBrowseScreen(
    onPlayChannel: (String, ZapContext) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val category by viewModel.category.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Text(
                text = "All Channels",
                style = MaterialTheme.typography.headlineSmall,
            )
            TvPill(
                label = "♥ Favorites only",
                selected = favoritesOnly,
                onClick = { viewModel.setFavoritesOnly(!favoritesOnly) },
            )
        }

        Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, top = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            item {
                TvPill(
                    label = "All",
                    selected = category == null,
                    onClick = { viewModel.setCategory(null) },
                    modifier = Modifier.focusRequester(initialFocus),
                )
            }
            items(categories, key = { it }) { item ->
                TvPill(
                    label = item,
                    selected = category == item,
                    onClick = { viewModel.setCategory(item) },
                )
            }
        }

        Text(
            text = "Country",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, top = 4.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            item {
                TvPill(
                    label = "All",
                    selected = country == null,
                    onClick = { viewModel.setCountry(null) },
                )
            }
            items(countries, key = { it }) { item ->
                TvPill(
                    label = "${countryFlagEmoji(item)} $item".trim(),
                    selected = country == item,
                    onClick = { viewModel.setCountry(item) },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
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
                    )
                }
            }
        }
    }
}
