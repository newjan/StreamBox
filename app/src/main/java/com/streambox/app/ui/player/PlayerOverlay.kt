package com.streambox.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambox.app.data.db.ChannelWithState

/**
 * Bottom control bar: favorite, prev/next channel, aspect-ratio toggle.
 * All controls are focusable for D-pad; left/right moves between them.
 */
@Composable
fun PlayerOverlay(
    channel: ChannelWithState?,
    isFavorite: Boolean,
    resizeMode: ResizeMode,
    onToggleFavorite: () -> Unit,
    onZapPrev: () -> Unit,
    onZapNext: () -> Unit,
    onCycleResize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        if (channel != null) {
            Text(
                text = channel.channel.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            IconButton(onClick = onZapPrev, modifier = Modifier.focusRequester(firstFocus)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous channel", tint = Color.White)
            }
            IconButton(onClick = onZapNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next channel", tint = Color.White)
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            IconButton(onClick = onCycleResize) {
                Icon(Icons.Default.AspectRatio, contentDescription = "Aspect ratio", tint = Color.White)
            }
            Text(
                text = when (resizeMode) {
                    ResizeMode.FIT -> "Fit"
                    ResizeMode.FILL -> "Fill"
                    ResizeMode.ZOOM -> "Zoom"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}
