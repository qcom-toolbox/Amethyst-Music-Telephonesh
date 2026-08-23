package com.amethyst_music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.amethyst_music.R
import com.amethyst_music.data.Playlist
import com.amethyst_music.data.Track
import com.amethyst_music.ui.components.PlaylistMosaic
import com.amethyst_music.ui.components.TrackRow
import com.amethyst_music.ui.theme.AmethystText
import com.amethyst_music.ui.theme.AmethystTextMuted

private val EDIT_ROW_HEIGHT = 66.dp

/**
 * Playlist detail page: hero (mosaic cover, name, Play/Shuffle) plus the track list. An Edit
 * toggle — shown only to the playlist's owner or an admin, mirroring what api.php's
 * `playlist_mod` actually allows — reveals drag-and-drop/▲▼ reordering and a per-track remove
 * button; renaming becomes an inline text field on the title instead of a popup. Outside edit
 * mode the page is a plain, read-only track list like [AlbumScreen]/[ArtistScreen].
 */
@Composable
fun PlaylistScreen(
    playlist: Playlist,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    coverUrlForTrack: (Track) -> String?,
    downloadedIds: Set<Int>,
    downloadingIds: Set<Int>,
    downloadProgress: Map<Int, Float>,
    canEdit: Boolean,
    editMode: Boolean,
    onToggleEditMode: () -> Unit,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onRemoveDownload: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onRemoveFromPlaylist: (Track) -> Unit,
    onPlayAll: () -> Unit = {},
    onPlayRandom: () -> Unit = {},
    onRename: (String) -> Unit,
    onSetVisibility: (Boolean) -> Unit,
    onReorder: (List<Int>) -> Unit,
    onOpenAddSongs: () -> Unit,
    onMiniPlayerClick: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    adminModeEnabled: Boolean = false,
    onEditTrack: ((Track) -> Unit)? = null,
    offlineOnlyMode: Boolean = false,
    selectedTab: Int = 2,
    onTabSelected: (Int) -> Unit = {},
    onClosePlaylist: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    val covers = remember(tracks) { tracks.take(8).map { coverUrlForTrack(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                PlaylistHero(
                    playlist = playlist,
                    trackCount = tracks.size,
                    covers = covers,
                    canEdit = canEdit,
                    editMode = editMode,
                    onToggleEditMode = onToggleEditMode,
                    onBack = onBack,
                    onRename = onRename,
                    onSetVisibility = onSetVisibility,
                )
            }
            if (tracks.isNotEmpty()) {
                item {
                    PlayControlsRow(
                        onPlayAll = onPlayAll,
                        onPlayRandom = onPlayRandom,
                    )
                }
            }
            if (editMode) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenAddSongs)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.add_song),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (canEdit) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSetVisibility(!playlist.isPublic) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (playlist.isPublic) Icons.Default.Public else Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (playlist.isPublic) R.string.public_playlist else R.string.private_playlist),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(if (playlist.isPublic) R.string.make_private else R.string.make_public),
                                    color = AmethystTextMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.empty_playlist),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (editMode) {
                item {
                    EditablePlaylistTrackList(
                        tracks = tracks,
                        coverUrlForTrack = coverUrlForTrack,
                        onReorder = onReorder,
                        onRemove = onRemoveFromPlaylist,
                    )
                }
            } else {
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
                            onRemoveFromPlaylist = if (canEdit) { { onRemoveFromPlaylist(track) } } else null,
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
private fun PlaylistHero(
    playlist: Playlist,
    trackCount: Int,
    covers: List<String?>,
    canEdit: Boolean,
    editMode: Boolean,
    onToggleEditMode: () -> Unit,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onSetVisibility: (Boolean) -> Unit,
) {
    var editingName by remember(playlist.id) { mutableStateOf(false) }
    var nameField by remember(playlist.id) { mutableStateOf(playlist.name) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(playlist.name) {
        if (!editingName) nameField = playlist.name
    }
    LaunchedEffect(editingName) {
        if (editingName) focusRequester.requestFocus()
    }

    fun commitRename() {
        editingName = false
        val trimmed = nameField.trim()
        if (trimmed.isNotEmpty() && trimmed != playlist.name) onRename(trimmed) else nameField = playlist.name
    }

    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        val backdropCover = covers.firstOrNull()
        if (backdropCover != null) {
            AsyncImage(
                model = backdropCover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(45.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (canEdit) {
                    IconButton(onClick = {
                        if (editingName) commitRename()
                        onToggleEditMode()
                    }) {
                        Icon(
                            if (editMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = stringResource(if (editMode) R.string.done else R.string.edit),
                            tint = Color.White,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                PlaylistMosaic(covers = covers, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (editingName) {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    singleLine = true,
                    textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitRename() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                        cursorColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            } else {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (editMode) it.clickable { editingName = true } else it },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tracks_count, trackCount),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
                Text(" • ", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Icon(
                    if (playlist.isPublic) Icons.Default.Public else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(if (playlist.isPublic) R.string.public_playlist else R.string.private_playlist),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Edit-mode track list: a drag handle (long-press then drag) plus ▲▼ buttons for step reordering
 * and a ✕ to remove — the plain [TrackRow] actions (download, add-to-playlist, …) are hidden here
 * to keep the editing view focused, matching the read-only page's own "clean view" the rest of
 * the time. */
@Composable
private fun EditablePlaylistTrackList(
    tracks: List<Track>,
    coverUrlForTrack: (Track) -> String?,
    onReorder: (List<Int>) -> Unit,
    onRemove: (Track) -> Unit,
) {
    var localTracks by remember(tracks) { mutableStateOf(tracks) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { EDIT_ROW_HEIGHT.toPx() }

    fun moveBy(from: Int, delta: Int) {
        val to = (from + delta).coerceIn(0, localTracks.lastIndex)
        if (to == from) return
        val updated = localTracks.toMutableList()
        val item = updated.removeAt(from)
        updated.add(to, item)
        localTracks = updated
        onReorder(updated.map { it.id })
    }

    Column {
        localTracks.forEachIndexed { index, track ->
            val isDragging = draggingId == track.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EDIT_ROW_HEIGHT)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                    .background(if (isDragging) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.reorder_track),
                    tint = AmethystTextMuted,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .pointerInput(track.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = track.id
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffsetPx = 0f
                                    onReorder(localTracks.map { it.id })
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffsetPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx += dragAmount.y
                                    val currentIndex = localTracks.indexOfFirst { it.id == track.id }
                                    if (currentIndex == -1) return@detectDragGesturesAfterLongPress
                                    if (dragOffsetPx > rowHeightPx / 2 && currentIndex < localTracks.lastIndex) {
                                        val updated = localTracks.toMutableList()
                                        val item = updated.removeAt(currentIndex)
                                        updated.add(currentIndex + 1, item)
                                        localTracks = updated
                                        dragOffsetPx -= rowHeightPx
                                    } else if (dragOffsetPx < -rowHeightPx / 2 && currentIndex > 0) {
                                        val updated = localTracks.toMutableList()
                                        val item = updated.removeAt(currentIndex)
                                        updated.add(currentIndex - 1, item)
                                        localTracks = updated
                                        dragOffsetPx += rowHeightPx
                                    }
                                },
                            )
                        },
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.outline),
                ) {
                    AsyncImage(
                        model = coverUrlForTrack(track),
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
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
                IconButton(onClick = { moveBy(index, -1) }, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up), tint = AmethystTextMuted)
                }
                IconButton(onClick = { moveBy(index, 1) }, enabled = index < localTracks.lastIndex) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down), tint = AmethystTextMuted)
                }
                IconButton(onClick = { onRemove(track) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_track), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
