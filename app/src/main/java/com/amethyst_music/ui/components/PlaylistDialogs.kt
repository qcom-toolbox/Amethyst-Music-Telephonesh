package com.amethyst_music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import com.amethyst_music.R
import com.amethyst_music.data.Playlist
import com.amethyst_music.data.Track
import com.amethyst_music.ui.theme.AmethystText
import com.amethyst_music.ui.theme.AmethystTextMuted

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.create_playlist), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = amethystFieldColors(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isPublic = !isPublic }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.public_playlist), color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, isPublic) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.create), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/** Focused "Add song" picker for the playlist edit page: search + checkbox list of tracks not
 * already in the playlist, replacing the old combined create/edit modal for this action. */
@Composable
fun AddSongsToPlaylistDialog(
    availableTracks: List<Track>,
    coverUrlForTrack: (Track) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (List<Track>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<Int>()) }
    val filtered = remember(availableTracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) availableTracks
        else availableTracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }
    val placeholder = rememberVectorPainter(Icons.Default.MusicNote)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.add_songs), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = amethystFieldColors(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (availableTracks.isEmpty()) {
                    Text(
                        stringResource(R.string.no_songs_available_to_add),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { it.id }) { track ->
                            val isSelected = selected.value.contains(track.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selected.value = if (isSelected) selected.value - track.id else selected.value + track.id
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = {
                                    selected.value = if (isSelected) selected.value - track.id else selected.value + track.id
                                })
                                AsyncImage(
                                    model = coverUrlForTrack(track),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                    placeholder = placeholder,
                                    error = placeholder,
                                    imageLoader = LocalImageLoader.current,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontWeight = FontWeight.Bold,
                                        color = AmethystText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = track.artist,
                                        color = AmethystTextMuted,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(availableTracks.filter { selected.value.contains(it.id) }) },
                enabled = selected.value.isNotEmpty(),
            ) {
                Text(stringResource(R.string.add_song), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.add_to_playlist), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        text = {
            if (playlists.isEmpty()) {
                Text(stringResource(R.string.no_playlists_available), color = MaterialTheme.colorScheme.onSurface)
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
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(playlist.name, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun amethystFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)
