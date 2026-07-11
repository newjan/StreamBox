package com.streambox.app.ui.groups

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.app.data.db.GroupCount
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.data.settings.ViewMode
import com.streambox.app.ui.shared.countryFlagEmoji

/**
 * Groups browser: pick category or country, grid or list, then drill into
 * the full channel list of that group. Focus-visible cards work with both
 * touch and D-pad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onOpenGroup: (HomeGroupBy, String) -> Unit,
    onBack: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val groupType by viewModel.groupType.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse Groups") },
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
        Column(modifier = Modifier.padding(padding)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                FilterChip(
                    selected = groupType == HomeGroupBy.CATEGORY,
                    onClick = { viewModel.setGroupType(HomeGroupBy.CATEGORY) },
                    label = { Text("Categories") },
                )
                FilterChip(
                    selected = groupType == HomeGroupBy.COUNTRY,
                    onClick = { viewModel.setGroupType(HomeGroupBy.COUNTRY) },
                    label = { Text("Countries") },
                )
            }

            when (viewMode) {
                ViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(groups, key = { it.name }) { group ->
                        GroupCard(
                            group = group,
                            groupType = groupType,
                            onClick = { onOpenGroup(groupType, group.name) },
                        )
                    }
                }
                ViewMode.LIST -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(groups, key = { it.name }) { group ->
                        GroupCard(
                            group = group,
                            groupType = groupType,
                            onClick = { onOpenGroup(groupType, group.name) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: GroupCount,
    groupType: HomeGroupBy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "groupScale")
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "groupBorder",
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (focused) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = modifier.scale(scale),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (groupType == HomeGroupBy.COUNTRY) {
                        "${countryFlagEmoji(group.name)} ${group.name}".trim()
                    } else {
                        group.name
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${group.count} channels",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
