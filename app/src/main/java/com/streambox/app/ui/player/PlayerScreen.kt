package com.streambox.app.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streambox.app.player.PlayerUiState
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val channel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()
    val channelChangeTick by viewModel.channelChangeTick.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()

    var overlayVisible by remember { mutableStateOf(false) }
    var overlayInteraction by remember { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableStateOf(false) }
    var centerLongPressFired by remember { mutableStateOf(false) }
    var channelListVisible by remember { mutableStateOf(false) }
    var panelInteraction by remember { mutableIntStateOf(0) }
    var panelHasFocus by remember { mutableStateOf(false) }
    val panelTopFocus = remember { FocusRequester() }
    val listChannels = viewModel.listChannels.collectAsLazyPagingItems()
    val panelGroups by viewModel.panelGroups.collectAsStateWithLifecycle()
    val panelGroupType by viewModel.panelGroupType.collectAsStateWithLifecycle()
    val panelSelectedGroup by viewModel.panelSelectedGroup.collectAsStateWithLifecycle()
    val favoritesCount by viewModel.favoritesCount.collectAsStateWithLifecycle()

    val rootFocus = remember { FocusRequester() }

    // Keep the screen awake while the player is open.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Channel-info banner for 3s on every channel change.
    LaunchedEffect(channelChangeTick) {
        if (channelChangeTick > 0) {
            bannerVisible = true
            delay(3000)
            bannerVisible = false
        }
    }

    // Auto-hide the controls overlay 5s after the last interaction.
    LaunchedEffect(overlayVisible, overlayInteraction) {
        if (overlayVisible) {
            delay(5000)
            overlayVisible = false
        }
    }

    // Auto-hide the channel panel 5s after the last key press or selection.
    LaunchedEffect(channelListVisible, panelInteraction) {
        if (channelListVisible) {
            delay(5000)
            channelListVisible = false
        }
    }

    // The root Box holds focus while no panel is open so it receives
    // D-pad events; focus moves into whichever panel appears.
    LaunchedEffect(overlayVisible, channelListVisible) {
        if (!overlayVisible && !channelListVisible) rootFocus.requestFocus()
    }
    LaunchedEffect(playerState) {
        if (playerState !is PlayerUiState.Error && !overlayVisible && !channelListVisible) {
            rootFocus.requestFocus()
        }
    }

    BackHandler(enabled = overlayVisible) { overlayVisible = false }
    // Composed after the overlay handler so it wins while the panel is open.
    BackHandler(enabled = channelListVisible) { channelListVisible = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val code = native.keyCode
                // While the channel panel is open, all keys go to the panel;
                // observing them here resets its auto-hide timer. If focus
                // ever fails to land inside the panel (lazy-list races), the
                // first D-pad press pulls it in instead of going dead.
                if (channelListVisible) {
                    if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                        panelInteraction++
                        val isDpad = code in intArrayOf(
                            AndroidKeyEvent.KEYCODE_DPAD_UP,
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        )
                        if (isDpad && !panelHasFocus) {
                            runCatching { panelTopFocus.requestFocus() }
                            return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                }
                when (native.action) {
                    AndroidKeyEvent.ACTION_DOWN -> when (code) {
                        AndroidKeyEvent.KEYCODE_MENU -> {
                            channelListVisible = true
                            overlayVisible = false
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!overlayVisible && playerState !is PlayerUiState.Error) {
                                channelListVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        -> {
                            viewModel.zapNext(); true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        -> {
                            viewModel.zapPrev(); true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        -> {
                            if (overlayVisible || playerState is PlayerUiState.Error) {
                                false // let the focused button handle it
                            } else {
                                // Long-press OK toggles favorite; short press opens overlay.
                                if (native.repeatCount >= 3 && !centerLongPressFired) {
                                    centerLongPressFired = true
                                    viewModel.toggleFavorite()
                                }
                                true
                            }
                        }
                        else -> false
                    }
                    AndroidKeyEvent.ACTION_UP -> when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        -> {
                            if (overlayVisible || playerState is PlayerUiState.Error) {
                                false
                            } else {
                                if (!centerLongPressFired) {
                                    overlayVisible = true
                                    overlayInteraction++
                                }
                                centerLongPressFired = false
                                true
                            }
                        }
                        else -> false
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                overlayVisible = !overlayVisible
                overlayInteraction++
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    // Never let the video surface take key focus away from
                    // the Compose UI on TV remotes.
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    player = viewModel.playerManager.player
                }
            },
            update = { playerView ->
                playerView.resizeMode = when (resizeMode) {
                    ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (playerState is PlayerUiState.Buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        (playerState as? PlayerUiState.Error)?.let { error ->
            PlayerErrorState(
                message = error.message,
                onRetry = viewModel::retry,
                onNextChannel = viewModel::zapNext,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = bannerVisible && channel != null && !channelListVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp),
        ) {
            channel?.let { ChannelBanner(channel = it, nowPlaying = nowPlaying) }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerOverlay(
                channel = channel,
                isFavorite = isFavorite,
                resizeMode = resizeMode,
                onToggleFavorite = {
                    viewModel.toggleFavorite(); overlayInteraction++
                },
                onZapPrev = { viewModel.zapPrev(); overlayInteraction++ },
                onZapNext = { viewModel.zapNext(); overlayInteraction++ },
                onCycleResize = { viewModel.cycleResizeMode(); overlayInteraction++ },
                onOpenChannelList = {
                    overlayVisible = false
                    channelListVisible = true
                },
            )
        }

        AnimatedVisibility(
            visible = channelListVisible,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onFocusChanged { panelHasFocus = it.hasFocus },
        ) {
            ChannelListPanel(
                topFocus = panelTopFocus,
                groups = panelGroups,
                groupType = panelGroupType,
                selectedGroup = panelSelectedGroup,
                favoritesCount = favoritesCount,
                channels = listChannels,
                currentKey = channel?.channel?.key,
                nowPlaying = nowPlaying,
                onGroupTypeChange = {
                    viewModel.setPanelGroupType(it); panelInteraction++
                },
                onGroupSelect = {
                    viewModel.selectPanelGroup(it); panelInteraction++
                },
                onSelect = {
                    viewModel.playByKey(it); panelInteraction++
                },
            )
        }
    }
}

@Composable
private fun PlayerErrorState(
    message: String,
    onRetry: () -> Unit,
    onNextChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(32.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                Text("Retry")
            }
            OutlinedButton(onClick = onNextChannel) {
                Text("Next channel")
            }
        }
    }
}
