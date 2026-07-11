package com.streambox.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambox.app.data.db.CustomCategoryWithCount

/**
 * "Add to list" overlay: toggle the current channel in/out of Favorites and
 * each custom list. The create-a-list form only appears when opened via
 * "+ New list" ([showCreate]); the long-press popover stays lists-only.
 */
@Composable
fun AddToListDialog(
    channelName: String,
    isFavorite: Boolean,
    lists: List<CustomCategoryWithCount>,
    memberIds: Set<Long>,
    onToggleFavorite: () -> Unit,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    showCreate: Boolean = false,
) {
    var newName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to list") },
        text = {
            Column {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleFavorite() }
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(
                            checked = isFavorite,
                            onCheckedChange = { onToggleFavorite() },
                        )
                        Text(
                            text = "♥ Favorites",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (lists.isEmpty()) {
                        Text(
                            text = if (showCreate) {
                                "No custom lists yet — create one below."
                            } else {
                                "No custom lists yet — use \"+ New list\" in the channel panel."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    lists.forEach { list ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(list.id) }
                                .padding(vertical = 2.dp),
                        ) {
                            Checkbox(
                                checked = list.id in memberIds,
                                onCheckedChange = { onToggle(list.id) },
                            )
                            Text(
                                text = list.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = list.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (showCreate) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("New list name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onCreate(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Create & add")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
