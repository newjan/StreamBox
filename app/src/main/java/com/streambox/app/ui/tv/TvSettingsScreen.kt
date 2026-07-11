package com.streambox.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streambox.app.data.ImportProgress
import com.streambox.app.ui.settings.SettingsViewModel

/**
 * TV settings: preset playlists as focusable pills, custom URL entry,
 * theme/EPG toggles, cache clearing — all D-pad reachable.
 */
@Composable
fun TvSettingsScreen(
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val savedUrl by viewModel.playlistUrl.collectAsStateWithLifecycle()
    val themeDark by viewModel.themeDark.collectAsStateWithLifecycle()
    val epgEnabled by viewModel.epgEnabled.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val hideDead by viewModel.hideDead.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val workingCount by viewModel.workingCount.collectAsStateWithLifecycle()

    var urlDraft by rememberSaveable { mutableStateOf("") }
    var urlEdited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(savedUrl) { if (!urlEdited) urlDraft = savedUrl }

    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text(
            "Playlist presets",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        viewModel.presets.forEachIndexed { index, preset ->
            TvPill(
                label = preset.name,
                selected = savedUrl == preset.url,
                onClick = {
                    urlDraft = preset.url
                    urlEdited = false
                    viewModel.savePlaylistUrl(preset.url)
                },
                modifier = if (index == 0) Modifier.focusRequester(initialFocus) else Modifier,
            )
        }

        Text(
            "Custom playlist URL",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedTextField(
            value = urlDraft,
            onValueChange = {
                urlDraft = it
                urlEdited = true
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TvPill(
            label = "Save & reload playlist",
            onClick = {
                if (urlDraft.isNotBlank()) {
                    viewModel.savePlaylistUrl(urlDraft)
                    urlEdited = false
                }
            },
        )

        when (val p = importProgress) {
            is ImportProgress.Running -> Text(
                "Updating playlist… ${p.count} channels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ImportProgress.Done -> Text(
                "Playlist updated: ${p.count} channels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ImportProgress.Failed -> Text(
                "Playlist update failed: ${p.message}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            null -> Unit
        }

        Text(
            "Options",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        TvPill(
            label = if (themeDark) "Theme: Dark" else "Theme: Light",
            onClick = { viewModel.setThemeDark(!themeDark) },
        )
        TvPill(
            label = if (epgEnabled) "Programme guide: On" else "Programme guide: Off",
            onClick = { viewModel.setEpgEnabled(!epgEnabled) },
        )
        Text(
            "Channel health",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        TvPill(
            label = if (hideDead) "Hide non-working channels: On" else "Hide non-working channels: Off",
            onClick = { viewModel.setHideDead(!hideDead) },
        )
        TvPill(
            label = if (scanProgress.running) "Stop scan" else "Scan channels now",
            onClick = { if (scanProgress.running) viewModel.stopScan() else viewModel.startScan() },
        )
        Text(
            text = when {
                scanProgress.running ->
                    "Checking… ${scanProgress.checked}/${scanProgress.total} " +
                        "(${scanProgress.working} working)"
                workingCount > 0 -> "$workingCount channels confirmed working"
                else -> "No scan yet — playing a channel also records its status"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TvPill(label = "Clear channel cache", onClick = viewModel::clearCache)
        TvPill(label = "About StreamBox", onClick = onOpenAbout)

        Text(
            text = "Content note: the default iptv-org index playlist already excludes adult channels.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
    }
}
