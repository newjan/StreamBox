package com.streambox.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.streambox.app.data.db.ChannelWithState
import com.streambox.app.ui.shared.LogoImage

/**
 * Left-side channel list shown over live playback (opened with MENU or
 * D-pad LEFT on TV, or the list button in the controls). Selecting a row
 * switches the stream; the panel stays open for further browsing.
 */
@Composable
fun ChannelListPanel(
    channels: LazyPagingItems<ChannelWithState>,
    currentKey: String?,
    nowPlaying: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    // Focus the list as soon as the first page lands so D-pad works instantly.
    LaunchedEffect(channels.itemCount > 0) {
        if (channels.itemCount > 0) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = "Channels",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            items(
                count = channels.itemCount,
                key = channels.itemKey { it.channel.key },
            ) { index ->
                channels[index]?.let { channel ->
                    PanelChannelRow(
                        channel = channel,
                        isCurrent = channel.channel.key == currentKey,
                        subtitle = if (channel.channel.key == currentKey) nowPlaying else null,
                        onClick = { onSelect(channel.channel.key) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelChannelRow(
    channel: ChannelWithState,
    isCurrent: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "panelRowBorder",
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = when {
                focused -> MaterialTheme.colorScheme.surfaceVariant
                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else -> Color.Transparent
            },
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            LogoImage(
                url = channel.channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = channel.channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = subtitle ?: channel.channel.category
                if (!sub.isNullOrBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (channel.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
