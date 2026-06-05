package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qualcomm_toolbox.amethyst.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import com.qualcomm_toolbox.amethyst.AppViewModel
import com.qualcomm_toolbox.amethyst.data.Playlist
import com.qualcomm_toolbox.amethyst.data.Track
import com.qualcomm_toolbox.amethyst.ui.components.MiniPlayerBar
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystAccent
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystBackground
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystBorder
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPanel
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPrimary
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystText
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystTextMuted

@Composable
fun MainScreen(
    vm: AppViewModel,
    siteName: String,
    selectedTab: Int,
    searchQuery: String,
    tracks: List<Track>,
    offlineTracks: List<Track>,
    playlists: List<Playlist>,
    isLoading: Boolean,
    offlineOnlyMode: Boolean,
    currentTrack: Track?,
    isPlaying: Boolean,
    coverUrlForTrack: (Track) -> String?,
    onTabSelected: (Int) -> Unit,
    onSearchChange: (String) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDownload: (Track) -> Unit,
    onRemoveDownload: (Track) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onExitOffline: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    val downloadedIds by vm.downloadedIds.collectAsState()
    val downloadingIds by vm.downloadingIds.collectAsState()
    val downloadProgress by vm.downloadProgress.collectAsState()
    val currentLanguage by vm.language.collectAsState()

    Scaffold(
        containerColor = AmethystBackground,
        bottomBar = {
            Column {
                if (currentTrack != null) {
                    MiniPlayerBar(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        coverUrl = coverUrlForTrack(currentTrack),
                        onClick = onMiniPlayerClick,
                        onPlayPause = onTogglePlay,
                    )
                }
                NavigationBar(
                    containerColor = AmethystPanel,
                    contentColor = AmethystText,
                ) {
                    if (!offlineOnlyMode) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { onTabSelected(0) },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_library)) },
                            colors = navColors(),
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { onTabSelected(1) },
                            icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_playlists)) },
                            colors = navColors(),
                        )
                    }
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { onTabSelected(2) },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_offline)) },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { onTabSelected(3) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                        colors = navColors(),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (offlineOnlyMode) stringResource(R.string.tab_offline) else siteName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmethystAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                
                if (offlineOnlyMode) {
                    IconButton(onClick = onExitOffline) {
                        Icon(Icons.Default.CloudOff, contentDescription = stringResource(R.string.exit_offline), tint = AmethystTextMuted)
                    }
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), tint = AmethystTextMuted)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.logout), tint = AmethystTextMuted)
                    }
                }
            }

            if (selectedTab == 0 || selectedTab == 2) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(50),
                    colors = fieldColors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val showLoading = isLoading &&
                tracks.isEmpty() &&
                offlineTracks.isEmpty() &&
                playlists.isEmpty() &&
                !offlineOnlyMode

            if (showLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmethystPrimary)
                }
            } else when (selectedTab) {
                0 -> TrackList(
                    title = stringResource(R.string.tab_library),
                    tracks = tracks,
                    downloadedIds = downloadedIds,
                    downloadingIds = downloadingIds,
                    downloadProgress = downloadProgress,
                    coverUrlForTrack = coverUrlForTrack,
                    showDownloadActions = true,
                    onTrackClick = onTrackClick,
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                )
                1 -> PlaylistList(
                    playlists = playlists,
                    onPlaylistClick = onPlaylistClick,
                )
                2 -> TrackList(
                    title = stringResource(R.string.tab_offline),
                    tracks = offlineTracks,
                    downloadedIds = downloadedIds,
                    downloadingIds = downloadingIds,
                    downloadProgress = downloadProgress,
                    coverUrlForTrack = coverUrlForTrack,
                    showDownloadActions = false,
                    onTrackClick = onTrackClick,
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                )
                3 -> SettingsScreen(
                    currentLanguage = currentLanguage,
                    onLanguageChange = vm::setLanguage,
                    onRefreshCache = vm::refreshCache
                )
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AmethystAccent,
    selectedTextColor = AmethystAccent,
    unselectedIconColor = AmethystTextMuted,
    unselectedTextColor = AmethystTextMuted,
    indicatorColor = AmethystBorder,
)

@Composable
private fun TrackList(
    title: String,
    tracks: List<Track>,
    downloadedIds: Set<Int>,
    downloadingIds: Set<Int>,
    downloadProgress: Map<Int, Float>,
    coverUrlForTrack: (Track) -> String?,
    showDownloadActions: Boolean,
    onTrackClick: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onRemoveDownload: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_tracks_found), color = AmethystTextMuted)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 80.dp), // pour le mini-player
    ) {
        item {
            Text(
                text = title,
                modifier = Modifier.padding(8.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AmethystText,
            )
        }

        items(
            items = tracks,
            key = { it.id },           // Critical for performance
            contentType = { "track_item" }
        ) { track ->
            TrackRow(
                track = track,
                cover = coverUrlForTrack(track),
                isDownloaded = downloadedIds.contains(track.id),
                isDownloading = downloadingIds.contains(track.id),
                downloadProgress = downloadProgress[track.id],
                showDownloadActions = showDownloadActions,
                onClick = { onTrackClick(track) },
                onDownload = { onDownload(track) },
                onRemoveDownload = { onRemoveDownload(track) },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    cover: String?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    showDownloadActions: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val placeholder = rememberVectorPainter(Icons.Default.MusicNote)
        AsyncImage(
            model = cover,
            contentDescription = track.title,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AmethystBorder),
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            imageLoader = LocalImageLoader.current,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontWeight = FontWeight.Bold,
                color = AmethystText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} • ${track.genre}",
                color = AmethystTextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isDownloading && downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(3.dp),
                    color = AmethystAccent,
                    trackColor = AmethystBorder,
                )
            }
        }

        val actionContent = remember(isDownloading, isDownloaded, showDownloadActions) {
            when {
                isDownloading -> "loading"
                showDownloadActions && isDownloaded -> "remove"
                showDownloadActions -> "download"
                isDownloaded -> "check"
                else -> "music"
            }
        }

        when (actionContent) {
            "loading" -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = AmethystAccent,
                    strokeWidth = 2.dp,
                )
            }
            "remove" -> {
                IconButton(onClick = onRemoveDownload) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_download), tint = AmethystTextMuted)
                }
            }
            "download" -> {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download), tint = AmethystAccent)
                }
            }
            "check" -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AmethystAccent, modifier = Modifier.size(24.dp))
            }
            else -> {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = AmethystPrimary)
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune playlist.", color = AmethystTextMuted)
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.tab_playlists),
                modifier = Modifier.padding(8.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AmethystText,
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AmethystPanel)
                    .clickable { onPlaylistClick(playlist) }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    tint = AmethystAccent,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(playlist.name, fontWeight = FontWeight.Bold, color = AmethystText)
                    Text(
                        "${playlist.songIds.size} titres",
                        color = AmethystTextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
