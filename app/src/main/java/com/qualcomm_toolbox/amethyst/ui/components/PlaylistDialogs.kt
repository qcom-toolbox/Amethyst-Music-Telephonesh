package com.qualcomm_toolbox.amethyst.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.data.Playlist
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystAccent
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPanel
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystText
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystTextMuted

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmethystPanel,
        title = { Text(stringResource(R.string.create_playlist), color = AmethystAccent, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                modifier = Modifier.fillMaxWidth(),
                colors = amethystFieldColors(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.create), color = AmethystAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = AmethystTextMuted)
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmethystPanel,
        title = { Text(stringResource(R.string.add_to_playlist), color = AmethystAccent, fontWeight = FontWeight.Bold) },
        text = {
            if (playlists.isEmpty()) {
                Text("Aucune playlist disponible.", color = AmethystText)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlaylistSelected(playlist) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = AmethystAccent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(playlist.name, color = AmethystText)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = AmethystTextMuted)
            }
        }
    )
}

@Composable
private fun amethystFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AmethystText,
    unfocusedTextColor = AmethystText,
    focusedContainerColor = AmethystPanel,
    unfocusedContainerColor = AmethystPanel,
    focusedBorderColor = AmethystAccent,
    unfocusedBorderColor = com.qualcomm_toolbox.amethyst.ui.theme.AmethystBorder,
    focusedLabelColor = AmethystAccent,
    unfocusedLabelColor = AmethystTextMuted,
    cursorColor = AmethystAccent,
)
