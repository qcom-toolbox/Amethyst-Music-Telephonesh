package com.amethyst_music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amethyst_music.R
import com.amethyst_music.data.Track
import com.amethyst_music.ui.components.TrackRow

/**
 * The caller's real listen history (action=history on the server), most recently played first —
 * mirrors index.php's dedicated history page, reached from the same header button rather than
 * folded into a Home carousel.
 */
@Composable
fun HistoryScreen(
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
    adminModeEnabled: Boolean = false,
    onEditTrack: ((Track) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = stringResource(R.string.recently_played),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
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
    }
}
