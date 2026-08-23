package com.amethyst_music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.amethyst_music.ui.theme.AmethystText

/**
 * Grid card for a playlist: a 4-cover mosaic (or a single cover for smaller playlists) instead
 * of a generic playlist icon. Deliberately compact so several fit on screen, mirroring how
 * [com.amethyst_music.ui.components.AlbumRow]/artist rows read next to it but sized for a grid
 * rather than a full-width list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: Playlist,
    covers: List<String?>,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (onDelete != null) showMenu = true },
            )
            .padding(8.dp),
    ) {
        Box {
            PlaylistMosaic(
                covers = covers,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            if (onDelete != null) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_playlist), color = AmethystText) },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AmethystText) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.tracks_count, playlist.songIds.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!playlist.isPublic) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.private_playlist),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        }
    }
}

/** Up to 4 covers arranged in a 2x2 mosaic; falls back to a single cover (or a placeholder icon
 * for an empty playlist) when there aren't enough distinct tracks to fill a grid. */
@Composable
fun PlaylistMosaic(
    covers: List<String?>,
    modifier: Modifier = Modifier,
) {
    val placeholder = rememberVectorPainter(Icons.AutoMirrored.Filled.PlaylistPlay)
    val distinct = covers.filterNotNull().distinct()

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center,
    ) {
        when {
            distinct.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        MosaicTile(distinct[0], placeholder, Modifier.weight(1f).fillMaxSize())
                        MosaicTile(distinct[1], placeholder, Modifier.weight(1f).fillMaxSize())
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        MosaicTile(distinct[2], placeholder, Modifier.weight(1f).fillMaxSize())
                        MosaicTile(distinct[3], placeholder, Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
            distinct.isNotEmpty() -> {
                MosaicTile(distinct[0], placeholder, Modifier.fillMaxSize())
            }
            else -> {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.4f),
                )
            }
        }
    }
}

@Composable
private fun MosaicTile(
    cover: String?,
    placeholder: androidx.compose.ui.graphics.painter.Painter,
    modifier: Modifier,
) {
    AsyncImage(
        model = cover,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        placeholder = placeholder,
        error = placeholder,
        imageLoader = LocalImageLoader.current,
    )
}
