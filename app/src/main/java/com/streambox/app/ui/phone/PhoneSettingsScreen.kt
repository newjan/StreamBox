package com.streambox.app.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.app.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingsScreen(
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val savedUrl by viewModel.playlistUrl.collectAsStateWithLifecycle()
    val themeDark by viewModel.themeDark.collectAsStateWithLifecycle()
    val epgEnabled by viewModel.epgEnabled.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()

    var urlDraft by rememberSaveable { mutableStateOf("") }
    var urlEdited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(savedUrl) { if (!urlEdited) urlDraft = savedUrl }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Playlist", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = urlDraft,
                onValueChange = {
                    urlDraft = it
                    urlEdited = true
                },
                singleLine = true,
                label = { Text("M3U playlist URL") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = {
                        viewModel.savePlaylistUrl(urlDraft)
                        urlEdited = false
                    },
                    enabled = urlDraft.isNotBlank(),
                ) { Text("Save & reload") }
                OutlinedButton(
                    onClick = viewModel::refreshNow,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Refresh now") }
            }

            Text(
                "Presets",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            viewModel.presets.forEach { preset ->
                FilterChip(
                    selected = savedUrl == preset.url,
                    onClick = {
                        urlDraft = preset.url
                        urlEdited = false
                        viewModel.savePlaylistUrl(preset.url)
                    },
                    label = { Text(preset.name) },
                )
            }

            ImportProgressBanner(importProgress)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Dark theme", modifier = Modifier.weight(1f))
                Switch(checked = themeDark, onCheckedChange = viewModel::setThemeDark)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Programme guide (EPG)")
                    Text(
                        "Fetches now-playing titles when the playlist provides a guide",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = epgEnabled, onCheckedChange = viewModel::setEpgEnabled)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            OutlinedButton(onClick = viewModel::clearCache) { Text("Clear channel cache") }

            Text(
                text = "Content note: the default iptv-org index playlist already excludes adult channels.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )

            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            ) { Text("About StreamBox") }
        }
    }
}
