package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import coil.compose.AsyncImage
import com.qualcomm_toolbox.amethyst.AppViewModel
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.data.Track
import com.qualcomm_toolbox.amethyst.ui.components.TrackRow
import com.qualcomm_toolbox.amethyst.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    loopMode: Int,
    shuffle: Boolean,
    coverUrl: String?,
    lyrics: String?,
    parsedLyrics: List<AppViewModel.LyricLine>,
    isLoadingLyrics: Boolean,
    showLyrics: Boolean,
    queue: List<Track>,
    downloadedIds: Set<Int>,
    downloadingIds: Set<Int>,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLoop: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleLyrics: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayTrackAt: (Int) -> Unit,
    onDownload: (Track) -> Unit,
    onAddToPlaylistForTrack: (Track) -> Unit,
    coverUrlProvider: (Track) -> String?,
) {
    var isLyricsMaximized by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }

    // Smooth position interpolation
    var smoothedPositionMs by remember(positionMs) { mutableLongStateOf(positionMs) }

    LaunchedEffect(isPlaying, positionMs) {
        if (isPlaying) {
            val startTime = System.currentTimeMillis()
            val startPos = positionMs
            while (true) {
                withFrameMillis {
                    smoothedPositionMs = startPos + (System.currentTimeMillis() - startTime)
                }
            }
        }
    }

    val displayPosition = smoothedPositionMs.coerceIn(0L, durationMs)

    val gradient = Brush.verticalGradient(
        colors = listOf(AmethystGradientStart, MaterialTheme.colorScheme.background),
    )

    // Interaction source to disable ripple on the background click consumer
    val interactionSource = remember { MutableInteractionSource() }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            // Important: This consumes all click events to prevent background interactions
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { /* Consume */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.close_player),
                        tint = AmethystText,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.now_playing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AndroidView(
                        factory = { context ->
                            MediaRouteButton(context).apply {
                                CastButtonFactory.setUpMediaRouteButton(context, this)
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp).size(32.dp)
                    )
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(R.string.add_to_playlist),
                            tint = AmethystText
                        )
                    }
                    IconButton(onClick = onToggleLyrics) {
                        Icon(
                            Icons.Default.Lyrics,
                            contentDescription = stringResource(R.string.lyrics),
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!isLyricsMaximized) {
                if (!showLyrics) Spacer(modifier = Modifier.weight(1f))

                // Album Art
                Card(
                    modifier = Modifier
                        .fillMaxWidth(if (showLyrics) 0.6f else 1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                if (!showLyrics) Spacer(modifier = Modifier.weight(0.2f)) else Spacer(modifier = Modifier.height(16.dp))

                // Track Info
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (showLyrics) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    Text(
                        text = track.title,
                        fontSize = if (showLyrics) 20.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        color = AmethystText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        fontSize = if (showLyrics) 14.sp else 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Slider
                val progress = if (durationMs > 0) {
                    sliderPosition ?: (displayPosition.toFloat() / durationMs)
                } else 0f

                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { v ->
                        sliderPosition = v
                        if (durationMs > 0) onSeek((v * durationMs).toLong())
                    },
                    onValueChangeFinished = {
                        sliderPosition = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = AmethystText,
                        activeTrackColor = AmethystText,
                        inactiveTrackColor = AmethystText.copy(alpha = 0.2f),
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentTime = if (sliderPosition != null && durationMs > 0) {
                        (sliderPosition!! * durationMs).toLong()
                    } else {
                        displayPosition
                    }
                    Text(
                        text = formatTime(currentTime),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                            tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(onClick = onPrevious) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.previous),
                                tint = AmethystText,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable { onPlayPause() },
                            shape = CircleShape,
                            color = AmethystText,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                                    tint = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        IconButton(onClick = onNext) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.next),
                                tint = AmethystText,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    IconButton(onClick = onToggleLoop) {
                        Icon(
                            imageVector = if (loopMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = stringResource(R.string.loop),
                            tint = if (loopMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (!showLyrics) Spacer(modifier = Modifier.weight(1f))
            }

            if (showLyrics) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(AmethystText.copy(alpha = 0.05f))
                        .clickable { isLyricsMaximized = !isLyricsMaximized }
                ) {
                    if (isLoadingLyrics) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (parsedLyrics.isNotEmpty()) {
                        val listState = rememberLazyListState()
                        val currentIndex = parsedLyrics.indexOfLast { it.timeMs <= displayPosition }.coerceAtLeast(0)

                        LaunchedEffect(currentIndex) {
                            listState.animateScrollToItem(currentIndex)
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            userScrollEnabled = isLyricsMaximized // Only allow manual scroll when fullscreen
                        ) {
                            itemsIndexed(parsedLyrics) { index, line ->
                                val isCurrent = index == currentIndex
                                Text(
                                    text = line.text,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = if (isCurrent) 22.sp else 18.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 32.sp,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .alpha(if (isCurrent) 1f else 0.5f)
                                        .clickable { onSeek(line.timeMs) }
                                )
                            }
                        }
                    } else {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(scrollState),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = lyrics ?: stringResource(R.string.no_lyrics),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp
                            )
                        }
                    }

                    if (isLyricsMaximized) {
                        IconButton(
                            onClick = { isLyricsMaximized = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = stringResource(R.string.exit_fullscreen), tint = AmethystText)
                        }
                    }
                }
            }
            
            if (!isLyricsMaximized) {
                // Bottom Handle for Queue
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .clickable { showQueue = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = AmethystText.copy(alpha = 0.5f)
                        )
                        Text(
                            text = stringResource(R.string.up_next),
                            color = AmethystText.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showQueue) {
        val currentIdx = queue.indexOfFirst { it.id == track.id }

        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.9f)) {
                Text(
                    text = stringResource(R.string.queue),
                    modifier = Modifier.padding(16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmethystText
                )
                val queueListState = rememberLazyListState()
                LaunchedEffect(currentIdx) {
                    if (currentIdx >= 0) queueListState.animateScrollToItem(currentIdx)
                }
                LazyColumn(
                    state = queueListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (queue.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_tracks_found), color = AmethystTextMuted)
                            }
                        }
                    }

                    itemsIndexed(queue) { index, qTrack ->
                        val isCurrent = qTrack.id == track.id
                        TrackRow(
                            track = qTrack,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            cover = coverUrlProvider(qTrack),
                            isDownloaded = downloadedIds.contains(qTrack.id),
                            isDownloading = downloadingIds.contains(qTrack.id),
                            downloadProgress = null,
                            showDownloadActions = true,
                            onClick = {
                                onPlayTrackAt(index)
                                showQueue = false
                            },
                            onDownload = { onDownload(qTrack) },
                            onRemoveDownload = { },
                            onAddToPlaylist = { onAddToPlaylistForTrack(qTrack) }
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}
