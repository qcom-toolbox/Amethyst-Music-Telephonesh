package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import com.qualcomm_toolbox.amethyst.AppViewModel
import com.qualcomm_toolbox.amethyst.SortOrder
import com.qualcomm_toolbox.amethyst.data.Playlist
import com.qualcomm_toolbox.amethyst.data.Track
import com.qualcomm_toolbox.amethyst.ui.components.AddToPlaylistDialog
import com.qualcomm_toolbox.amethyst.ui.components.CreatePlaylistDialog
import com.qualcomm_toolbox.amethyst.ui.components.MiniPlayerBar
import androidx.compose.material.icons.filled.Edit
import com.qualcomm_toolbox.amethyst.ui.components.EditTrackDialog
import com.qualcomm_toolbox.amethyst.ui.components.PlayingVisualizer
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystAccent
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
    onUploadTrack: (String, String, String, ByteArray, String, ByteArray?, String?) -> Unit,
    homeRecommended: List<Track> = emptyList(),
    homePopular: List<Track> = emptyList(),
    homeHiddenGems: List<Track> = emptyList(),
    backgroundColor: Long = 0xFF0F0C1D,
    useHarmony: Boolean = true,
    onThemeChange: (Long, Boolean) -> Unit = { _, _ -> },
) {
    val downloadedIds by vm.downloadedIds.collectAsState()
    val downloadingIds by vm.downloadingIds.collectAsState()
    val downloadProgress by vm.downloadProgress.collectAsState()
    val currentLanguage by vm.language.collectAsState()
    val genres by vm.genres.collectAsState()
    val currentPlaylist by vm.currentPlaylist.collectAsState()
    val currentPlaylistTracks by vm.currentPlaylistTracks.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val adminModeEnabled by vm.adminModeEnabled.collectAsState()

    var showUploadDialog by remember { mutableStateOf(false) }
    var trackToEdit by remember { mutableStateOf<Track?>(null) }
    var showPlaylistCreateDialog by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val selectedGenres by vm.selectedGenres.collectAsState()
    val sortOrder by vm.sortOrder.collectAsState()

    if (showUploadDialog) {
        UploadDialog(
            genres = genres,
            onDismiss = { showUploadDialog = false },
            onUpload = { t, a, g, m, mn, c, cn ->
                onUploadTrack(t, a, g, m, mn, c, cn)
                showUploadDialog = false
            }
        )
    }

    if (showPlaylistCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showPlaylistCreateDialog = false },
            onCreate = { name ->
                vm.createPlaylist(name)
                showPlaylistCreateDialog = false
            }
        )
    }

    trackToEdit?.let { track ->
        EditTrackDialog(
            track = track,
            genres = genres,
            onDismiss = { trackToEdit = null },
            onSave = { id, title, artist, genre, cover, coverName ->
                vm.editTrack(id, title, artist, genre, cover, coverName)
                trackToEdit = null
            },
            onDelete = { id ->
                vm.deleteTrack(id)
                trackToEdit = null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_home)) },
                            colors = navColors(),
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { 
                                vm.closePlaylist()
                                onTabSelected(1) 
                            },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_library)) },
                            colors = navColors(),
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { onTabSelected(2) },
                            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_playlists)) },
                            colors = navColors(),
                        )
                    }
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { 
                            vm.closePlaylist()
                            onTabSelected(3) 
                        },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_offline)) },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { 
                            vm.closePlaylist()
                            onTabSelected(4) 
                        },
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
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                
                if (currentPlaylist != null && selectedTab == 2) {
                    IconButton(onClick = { vm.closePlaylist() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = AmethystTextMuted)
                    }
                }

                if (offlineOnlyMode) {
                    IconButton(onClick = onExitOffline) {
                        Icon(Icons.Default.CloudOff, contentDescription = stringResource(R.string.exit_offline), tint = AmethystTextMuted)
                    }
                } else {
                    if (selectedTab == 2) {
                        IconButton(onClick = { showPlaylistCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_playlist), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { showUploadDialog = true }) {
                        Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.upload), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.logout), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (selectedTab == 1 || selectedTab == 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = amethystFieldColors(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter_sort),
                                tint = if (selectedGenres.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilterSortMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            genres = genres,
                            selectedGenres = selectedGenres,
                            onGenreToggle = vm::toggleGenre,
                            onClearFilters = vm::clearGenreFilters,
                            currentSort = sortOrder,
                            onSortSelect = vm::setSortOrder
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = AmethystAccent,
                    trackColor = MaterialTheme.colorScheme.background
                )
            }

            when (selectedTab) {
                0 -> HomeScreen(
                    recommended = homeRecommended,
                    popular = homePopular,
                    hiddenGems = homeHiddenGems,
                    coverUrlForTrack = coverUrlForTrack,
                    onTrackClick = onTrackClick
                )
                1 -> TrackList(
                    tracks = tracks,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    downloadedIds = downloadedIds,
                    downloadingIds = downloadingIds,
                    downloadProgress = downloadProgress,
                    coverUrlForTrack = coverUrlForTrack,
                    showDownloadActions = true,
                    onTrackClick = onTrackClick,
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                    onAddToPlaylist = { vm.showAddToPlaylist(it) },
                    adminModeEnabled = adminModeEnabled,
                    onEditTrack = { trackToEdit = it }
                )
                2 -> if (currentPlaylist != null) {
                    TrackList(
                        tracks = currentPlaylistTracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        downloadedIds = downloadedIds,
                        downloadingIds = downloadingIds,
                        downloadProgress = downloadProgress,
                        coverUrlForTrack = coverUrlForTrack,
                        showDownloadActions = true,
                        onTrackClick = onTrackClick,
                        onDownload = onDownload,
                        onRemoveDownload = onRemoveDownload,
                        onAddToPlaylist = { vm.showAddToPlaylist(it) },
                        adminModeEnabled = adminModeEnabled,
                        onEditTrack = { trackToEdit = it }
                    )
                } else {
                    PlaylistList(
                        playlists = playlists,
                        onPlaylistClick = { vm.openPlaylist(it) },
                        onDeletePlaylist = { vm.deletePlaylist(it) }
                    )
                }
                3 -> TrackList(
                    tracks = offlineTracks,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    downloadedIds = downloadedIds,
                    downloadingIds = downloadingIds,
                    downloadProgress = downloadProgress,
                    coverUrlForTrack = coverUrlForTrack,
                    showDownloadActions = false,
                    onTrackClick = onTrackClick,
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                    adminModeEnabled = adminModeEnabled,
                    onEditTrack = { trackToEdit = it }
                )
                4 -> SettingsScreen(
                    currentLanguage = currentLanguage,
                    onLanguageChange = vm::setLanguage,
                    currentBackgroundColor = backgroundColor,
                    currentUseHarmony = useHarmony,
                    onThemeChange = onThemeChange,
                    onRefreshCache = vm::refreshCache,
                    isAdmin = isAdmin,
                    adminModeEnabled = adminModeEnabled,
                    onAdminModeChange = vm::setAdminModeEnabled
                )
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
)

@Composable
fun FilterSortMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    genres: List<String>,
    selectedGenres: Set<String>,
    onGenreToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    currentSort: SortOrder,
    onSortSelect: (SortOrder) -> Unit
) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            // Sort Section FIRST
            Text(
                text = "Sort by",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_popularity), color = MaterialTheme.colorScheme.onSurface) },
                onClick = { onSortSelect(SortOrder.POPULARITY); onDismissRequest() },
                leadingIcon = { if (currentSort == SortOrder.POPULARITY) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_title), color = MaterialTheme.colorScheme.onSurface) },
                onClick = { onSortSelect(SortOrder.TITLE_ASC); onDismissRequest() },
                leadingIcon = { if (currentSort == SortOrder.TITLE_ASC) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_artist), color = MaterialTheme.colorScheme.onSurface) },
                onClick = { onSortSelect(SortOrder.ARTIST_ASC); onDismissRequest() },
                leadingIcon = { if (currentSort == SortOrder.ARTIST_ASC) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_newest), color = MaterialTheme.colorScheme.onSurface) },
                onClick = { onSortSelect(SortOrder.DATE_UPLOAD_DESC); onDismissRequest() },
                leadingIcon = { if (currentSort == SortOrder.DATE_UPLOAD_DESC) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            // Genre Section
            Text(
                text = "Genre",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.genre_all), color = MaterialTheme.colorScheme.onSurface) },
                onClick = { onClearFilters(); onDismissRequest() },
                leadingIcon = { if (selectedGenres.isEmpty()) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
            genres.forEach { genre ->
                DropdownMenuItem(
                    text = { Text(genre, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { onGenreToggle(genre) }, // Don't dismiss so user can select multiple
                    leadingIcon = { if (selectedGenres.contains(genre)) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }
        }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    downloadedIds: Set<Int>,
    downloadingIds: Set<Int>,
    downloadProgress: Map<Int, Float>,
    coverUrlForTrack: (Track) -> String?,
    showDownloadActions: Boolean,
    onTrackClick: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onRemoveDownload: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    adminModeEnabled: Boolean = false,
    onEditTrack: ((Track) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 80.dp), // pour le mini-player
    ) {
        if (tracks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_tracks_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(
            items = tracks,
            key = { it.id },           // Critical for performance
            contentType = { "track_item" }
        ) { track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrack?.id,
                isPlaying = isPlaying,
                cover = coverUrlForTrack(track),
                isDownloaded = downloadedIds.contains(track.id),
                isDownloading = downloadingIds.contains(track.id),
                downloadProgress = downloadProgress[track.id],
                showDownloadActions = showDownloadActions,
                onClick = { onTrackClick(track) },
                onDownload = { onDownload(track) },
                onRemoveDownload = { onRemoveDownload(track) },
                onAddToPlaylist = onAddToPlaylist?.let { { it(track) } },
                adminModeEnabled = adminModeEnabled,
                onEditTrack = onEditTrack?.let { { it(track) } },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    cover: String?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    showDownloadActions: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    adminModeEnabled: Boolean = false,
    onEditTrack: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (isCurrent) AmethystPanel.copy(alpha = 0.5f) else Color.Transparent)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val placeholder = rememberVectorPainter(Icons.Default.MusicNote)
        
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = cover,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                imageLoader = LocalImageLoader.current,
            )

            if (isCurrent && isPlaying) {
                PlayingVisualizer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} • ${track.genre}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        val actionStatus = remember(isDownloading, isDownloaded) {
            when {
                isDownloading -> "loading"
                isDownloaded -> "check"
                else -> "music"
            }
        }

        when (actionStatus) {
            "loading" -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = AmethystAccent,
                    strokeWidth = 2.dp,
                )
            }
            "check" -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AmethystAccent, modifier = Modifier.size(24.dp))
            }
            else -> {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = AmethystPrimary)
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = AmethystTextMuted)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(AmethystPanel)
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
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = AmethystAccent) }
                    )
                }

                if (showDownloadActions) {
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
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = AmethystAccent) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
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
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            var showMenu by remember { mutableStateOf(false) }

            Box {
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
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playlist.name, fontWeight = FontWeight.Bold, color = AmethystText)
                        Text(
                            "${playlist.songIds.size} titres",
                            color = AmethystTextMuted,
                            fontSize = 13.sp,
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = AmethystTextMuted)
                    }
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(AmethystPanel)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_playlist), color = AmethystText) },
                        onClick = {
                            onDeletePlaylist(playlist)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AmethystText) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDialog(
    genres: List<String>,
    onDismiss: () -> Unit,
    onUpload: (title: String, artist: String, genre: String, music: ByteArray, musicName: String, cover: ByteArray?, coverName: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Autre") }
    var musicUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var musicName by remember { mutableStateOf("") }
    var coverUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var coverName by remember { mutableStateOf("") }

    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            musicUri = it
            readUriBytes(context, it)?.let { pair ->
                musicName = pair.second

                // Auto-fill from metadata
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, it)
                    val mTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val mArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    retriever.release()

                    if (!mTitle.isNullOrBlank()) title = mTitle
                    if (!mArtist.isNullOrBlank()) artist = mArtist
                } catch (_: Exception) {}

                // Fallback to filename parsing
                if (title.isEmpty() || artist.isEmpty()) {
                    val rawName = pair.second.substringBeforeLast(".")
                    if (rawName.contains(" - ")) {
                        val parts = rawName.split(" - ", limit = 2)
                        if (artist.isEmpty()) artist = parts[0].trim()
                        if (title.isEmpty()) title = parts[1].trim()
                    } else if (title.isEmpty()) {
                        title = rawName
                    }
                }
            }
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coverUri = it
            readUriBytes(context, it)?.let { pair ->
                coverName = pair.second
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmethystPanel,
        title = { Text(stringResource(R.string.upload), color = AmethystAccent, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = amethystFieldColors(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artiste") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = amethystFieldColors(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = !isExpanded }
                ) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Genre") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        colors = amethystFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false },
                        modifier = Modifier.background(AmethystPanel)
                    ) {
                        genres.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g, color = AmethystText) },
                                onClick = {
                                    genre = g
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { musicPicker.launch("audio/*") },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AmethystPrimary)
                    ) {
                        Text("Musique")
                    }
                    Text(
                        text = musicName.ifEmpty { "Aucun fichier" },
                        color = AmethystTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { coverPicker.launch("image/*") },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AmethystBorder)
                    ) {
                        Text("Pochette")
                    }
                    Text(
                        text = coverName.ifEmpty { "Optionnel" },
                        color = AmethystTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mUri = musicUri
                    if (mUri != null) {
                        val mData = readUriBytes(context, mUri)
                        val cData = coverUri?.let { readUriBytes(context, it) }
                        if (mData != null) {
                            onUpload(
                                title,
                                artist,
                                genre,
                                mData.first,
                                mData.second,
                                cData?.first,
                                cData?.second
                            )
                        }
                    }
                },
                enabled = musicUri != null
            ) {
                Text(stringResource(R.string.upload), color = AmethystAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = AmethystTextMuted)
            }
        }
    )
}

private fun readUriBytes(context: android.content.Context, uri: android.net.Uri): Pair<ByteArray, String>? {
    return try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.readBytes()
        inputStream.close()

        var fileName = "file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        bytes to fileName
    } catch (e: Exception) {
        null
    }
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
