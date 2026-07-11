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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.streambox.app.data.db.GroupCount
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.ui.shared.LogoImage
import com.streambox.app.ui.shared.countryFlagEmoji

/**
 * Two-level channel browser shown over live playback: groups (categories or
 * countries) on the left, the selected group's channels on the right.
 * D-pad LEFT/RIGHT moves between the columns; OK selects; BACK closes.
 */
@Composable
fun ChannelListPanel(
    groups: List<GroupCount>,
    groupType: HomeGroupBy,
    selectedGroup: String?,
    channels: LazyPagingItems<ChannelWithState>,
    currentKey: String?,
    nowPlaying: String?,
    onGroupTypeChange: (HomeGroupBy) -> Unit,
    onGroupSelect: (String?) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupFocus = remember { FocusRequester() }
    val groupListState = rememberLazyListState()

    // Open on the selected group: scroll to it and take focus.
    LaunchedEffect(groups.isNotEmpty()) {
        if (groups.isNotEmpty()) {
            val index = selectedGroup?.let { sel -> groups.indexOfFirst { it.name == sel } } ?: -1
            if (index >= 0) groupListState.scrollToItem(index + 1) // +1 for "All"
            runCatching { groupFocus.requestFocus() }
        }
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(vertical = 12.dp),
    ) {
        // ---- Level 1: groups ----
        Column(modifier = Modifier.width(230.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                PanelPill(
                    label = "Categories",
                    selected = groupType == HomeGroupBy.CATEGORY,
                    onClick = { onGroupTypeChange(HomeGroupBy.CATEGORY) },
                )
                PanelPill(
                    label = "Countries",
                    selected = groupType == HomeGroupBy.COUNTRY,
                    onClick = { onGroupTypeChange(HomeGroupBy.COUNTRY) },
                )
            }
            LazyColumn(
                state = groupListState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                item(key = "__all__") {
                    GroupRow(
                        label = "All channels",
                        count = null,
                        isSelected = selectedGroup == null,
                        onClick = { onGroupSelect(null) },
                        modifier = if (selectedGroup == null) {
                            Modifier.focusRequester(groupFocus)
                        } else {
                            Modifier
                        },
                    )
                }
                items(count = groups.size, key = { groups[it].name }) { index ->
                    val group = groups[index]
                    GroupRow(
                        label = if (groupType == HomeGroupBy.COUNTRY) {
                            "${countryFlagEmoji(group.name)} ${group.name}".trim()
                        } else {
                            group.name
                        },
                        count = group.count,
                        isSelected = selectedGroup == group.name,
                        onClick = { onGroupSelect(group.name) },
                        modifier = if (selectedGroup == group.name) {
                            Modifier.focusRequester(groupFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // ---- Level 2: channels of the selected group ----
        Column(modifier = Modifier.width(330.dp)) {
            Text(
                text = when {
                    selectedGroup == null -> "All channels"
                    groupType == HomeGroupBy.COUNTRY ->
                        "${countryFlagEmoji(selectedGroup)} $selectedGroup".trim()
                    else -> selectedGroup
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        border = BorderStroke(
            2.dp,
            if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GroupRow(
    label: String,
    count: Int?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "groupRowBorder",
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = when {
                focused -> MaterialTheme.colorScheme.surfaceVariant
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else -> Color.Transparent
            },
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
