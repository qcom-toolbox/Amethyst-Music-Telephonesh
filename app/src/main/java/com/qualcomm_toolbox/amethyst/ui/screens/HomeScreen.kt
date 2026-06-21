package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.data.Track
import com.qualcomm_toolbox.amethyst.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recommended: List<Track>,
    popular: List<Track>,
    hiddenGems: List<Track>,
    coverUrlForTrack: (Track) -> String?,
    onTrackClick: (Track) -> Unit,
    downloadedIds: Set<Int> = emptySet(),
    downloadingIds: Set<Int> = emptySet(),
    onDownload: (Track) -> Unit = {},
    onRemoveDownload: (Track) -> Unit = {},
    onAddToPlaylist: ((Track) -> Unit)? = null,
    adminModeEnabled: Boolean = false,
    onEditTrack: ((Track) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                HomeSection(
                    title = stringResource(R.string.home_for_you),
                    tracks = recommended,
                    coverUrlForTrack = coverUrlForTrack,
                    onTrackClick = onTrackClick,
                    downloadedIds = downloadedIds,
                    downloadingIds = downloadingIds,
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    adminModeEnabled = adminModeEnabled,
                    onEditTrack = onEditTrack
                )
            }
            item {
                HomeSection(
                    title = stringResource(R.string.home_popular),
                    tracks = popular,
                    coverUrlForTrack = coverUrlForTrack,
                    onTrackClick = onTrackClick,
                    downloadedIds = downloadedIds,
                    downloadingIds = downloadingIds,
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    adminModeEnabled = adminModeEnabled,
                    onEditTrack = onEditTrack
                )
            }
            item {
                HomeSection(
                    title = stringResource(R.string.home_hidden_gems),
                    tracks = hiddenGems,
                    coverUrlForTrack = coverUrlForTrack,
                    onTrackClick = onTrackClick,
                    downloadedIds = downloadedIds,
                    downloadingIds = downloadingIds,
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    adminModeEnabled = adminModeEnabled,
                    onEditTrack = onEditTrack
                )
            }
        }
    }
}

@Composable
fun HomeSection(
    title: String,
    tracks: List<Track>,
    coverUrlForTrack: (Track) -> String?,
    onTrackClick: (Track) -> Unit,
    downloadedIds: Set<Int>,
    downloadingIds: Set<Int>,
    onDownload: (Track) -> Unit,
    onRemoveDownload: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)?,
    adminModeEnabled: Boolean,
    onEditTrack: ((Track) -> Unit)?,
) {
    if (tracks.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks) { track ->
                HomeTrackCard(
                    track = track,
                    cover = coverUrlForTrack(track),
                    onClick = { onTrackClick(track) },
                    isDownloaded = downloadedIds.contains(track.id),
                    isDownloading = downloadingIds.contains(track.id),
                    onDownload = { onDownload(track) },
                    onRemoveDownload = { onRemoveDownload(track) },
                    onAddToPlaylist = onAddToPlaylist?.let { { it(track) } },
                    adminModeEnabled = adminModeEnabled,
                    onEditTrack = onEditTrack?.let { { it(track) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrackCard(
    track: Track,
    cover: String?,
    onClick: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    adminModeEnabled: Boolean = false,
    onEditTrack: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(4.dp)
    ) {
        Box {
            AsyncImage(
                model = cover,
                contentDescription = track.title,
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { showMenu = true },
                        onLongClick = { showMenu = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                if (onAddToPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_playlist), color = AmethystText) },
                        onClick = {
                            onAddToPlaylist()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = AmethystText) }
                    )
                }

                if (adminModeEnabled && onEditTrack != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_metadata), color = AmethystText) },
                        onClick = {
                            onEditTrack()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }

                if (isDownloaded) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_download), color = AmethystText) },
                        onClick = {
                            onRemoveDownload()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AmethystText) }
                    )
                } else if (!isDownloading) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download), color = AmethystText) },
                        onClick = {
                            onDownload()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
