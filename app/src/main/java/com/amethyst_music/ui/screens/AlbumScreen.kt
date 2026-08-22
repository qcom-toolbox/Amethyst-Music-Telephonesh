package com.amethyst_music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amethyst_music.R
import com.amethyst_music.data.Track
import com.amethyst_music.ui.components.TrackRow

/**
 * Derived entirely from the already-fetched track list, filtered to tracks sharing this album
 * name — no API call, mirroring [ArtistScreen]. Reached only by tapping an album name/card that
 * came from a real track, so the empty-tracks case is a fallback rather than an expected path.
 */
@Composable
fun AlbumScreen(
    albumName: String,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    coverUrlForTrack: (Track) -> String?,
    downloadedIds: Set<Int>,
    downloadingIds: Set<Int>,
    downloadProgress: Map<Int, Float>,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onRemoveDownload: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayAll: () -> Unit = {},
    onPlayRandom: () -> Unit = {},
    onMiniPlayerClick: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    adminModeEnabled: Boolean = false,
    onEditTrack: ((Track) -> Unit)? = null,
    offlineOnlyMode: Boolean = false,
    selectedTab: Int = 1,
    onTabSelected: (Int) -> Unit = {},
    onClosePlaylist: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    // id is used as the recency proxy, matching the app's own "Newest" sort — the API
    // doesn't return an upload timestamp.
    val mostRecent = remember(tracks) { tracks.maxByOrNull { it.id } }
    val coverUrl = mostRecent?.let { coverUrlForTrack(it) }
    val artistName = mostRecent?.artist

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                AlbumHero(
                    albumName = albumName,
                    artistName = artistName,
                    trackCount = tracks.size,
                    coverUrl = coverUrl,
                    onBack = onBack,
                )
            }
            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_tracks_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    PlayControlsRow(
                        onPlayAll = onPlayAll,
                        onPlayRandom = onPlayRandom,
                    )
                }
                items(tracks, key = { it.id }) { track ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        TrackRow(
                            track = track,
                            isCurrent = track.id == currentTrack?.id,
                            isPlaying = isPlaying,
                            cover = coverUrlForTrack(track),
                            isDownloaded = downloadedIds.contains(track.id),
                            isDownloading = downloadingIds.contains(track.id),
                            downloadProgress = downloadProgress[track.id],
                            showDownloadActions = true,
                            onClick = { onTrackClick(track) },
                            onDownload = { onDownload(track) },
                            onRemoveDownload = { onRemoveDownload(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            adminModeEnabled = adminModeEnabled,
                            onEditTrack = onEditTrack?.let { { it(track) } },
                            artistClickEnabled = false,
                        )
                    }
                }
            }
        }
        AppBottomBar(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            coverUrlForTrack = coverUrlForTrack,
            onMiniPlayerClick = onMiniPlayerClick,
            onTogglePlay = onPlayPause,
            onNextTrack = onNext,
            onPreviousTrack = onPrevious,
            offlineOnlyMode = offlineOnlyMode,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            onClosePlaylist = onClosePlaylist,
        )
    }
}

@Composable
private fun AlbumHero(
    albumName: String,
    artistName: String?,
    trackCount: Int,
    coverUrl: String?,
    onBack: () -> Unit,
) {
    val placeholder = rememberVectorPainter(Icons.Default.MusicNote)

    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(45.dp),
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
        )
        // Darken so title/back-nav text stays legible over the blurred cover.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.outline),
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = albumName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = albumName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!artistName.isNullOrBlank()) {
                Text(
                    text = artistName,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
            Text(
                text = stringResource(R.string.tracks_count, trackCount),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 20.dp),
            )
        }
    }
}
