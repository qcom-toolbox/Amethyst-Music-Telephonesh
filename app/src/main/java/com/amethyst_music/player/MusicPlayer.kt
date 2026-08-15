package com.amethyst_music.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.amethyst_music.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class MusicPlayer(private val appContext: Context) {
    val equalizerManager = EqualizerManager(appContext)
    private var exoPlayer: ExoPlayer = buildExoPlayer(null)
    
    private val castContext: CastContext? by lazy {
        try { CastContext.getSharedInstance(appContext) } catch (e: Exception) { null }
    }

    private val castPlayer: CastPlayer? by lazy {
        castContext?.let { context ->
            CastPlayer(context).apply {
                setSessionAvailabilityListener(object : SessionAvailabilityListener {
                    override fun onCastSessionAvailable() {
                        switchToPlayer(this@apply)
                    }

                    override fun onCastSessionUnavailable() {
                        switchToPlayer(exoPlayer)
                    }
                })
            }
        }
    }

    private var currentPlayer: Player = exoPlayer

    private fun switchToPlayer(newPlayer: Player) {
        if (currentPlayer === newPlayer) return

        // Capture position and index BEFORE pausing or removing listener
        // Use a small safety margin for position as CastPlayer might report 0 during teardown
        val position = currentPlayer.currentPosition.coerceAtLeast(0L)
        val globalIndex = windowStart + currentPlayer.currentMediaItemIndex
        val wasPlaying = currentPlayer.isPlaying
        val currentRepeatMode = currentPlayer.repeatMode

        currentPlayer.removeListener(listener)
        currentPlayer.pause()

        currentPlayer = newPlayer
        currentPlayer.addListener(listener)
        currentPlayer.repeatMode = currentRepeatMode
        currentPlayer.setPlaybackSpeed(playbackSpeed)

        // Rebuild only a window of media items for the new player (especially important
        // for Cast) instead of mapping the whole library synchronously.
        val list = activeList()
        val provider = streamUrlProvider
        if (list.isNotEmpty() && provider != null) {
            val safeIndex = globalIndex.coerceIn(0, list.lastIndex)
            if (shuffle) {
                shuffleIndex = safeIndex
                queueIndex = queue.indexOf(list[safeIndex]).coerceAtLeast(0)
            } else {
                queueIndex = safeIndex
            }
            loadWindowed(list, safeIndex, provider, position, autoplay = wasPlaying)
        } else {
            val items = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it) }
            val safeIndex = if (items.isNotEmpty()) 0.coerceIn(0, items.size - 1) else 0
            currentPlayer.setMediaItems(items, safeIndex, position)
            currentPlayer.prepare()
            if (wasPlaying) currentPlayer.play()
        }

        // Sync local state flows immediately
        _isPlaying.value = wasPlaying
        _positionMs.value = position

        activePlayer = currentPlayer
        // Force a MediaSession sync in the service
        startPlaybackService()
    }

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queueFlow: StateFlow<List<Track>> = _queue.asStateFlow()
    var queue: List<Track>
        get() = _queue.value
        private set(value) { _queue.value = value }

    // The active playback order: shuffledQueue when shuffle is on, queue otherwise
    private val _activeQueue = MutableStateFlow<List<Track>>(emptyList())
    val activeQueueFlow: StateFlow<List<Track>> = _activeQueue.asStateFlow()

    private var queueIndex = 0

    // Manual shuffle: pre-generated shuffled order so we never reshuffle mid-queue
    private var shuffledQueue: List<Track> = emptyList()
    private var shuffleIndex = 0   // position within shuffledQueue

    private val _loopMode = MutableStateFlow(0)
    val loopModeFlow: StateFlow<Int> = _loopMode.asStateFlow()
    var loopMode: Int
        get() = _loopMode.value
        private set(value) { _loopMode.value = value }

    private val _shuffle = MutableStateFlow(false)
    val shuffleFlow: StateFlow<Boolean> = _shuffle.asStateFlow()
    var shuffle: Boolean
        get() = _shuffle.value
        private set(value) { _shuffle.value = value }

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeedFlow: StateFlow<Float> = _playbackSpeed.asStateFlow()
    private val playbackSpeed: Float get() = _playbackSpeed.value

    private var streamUrlProvider: ((Track, Boolean) -> String)? = null
    private var incrementPlayCallback: ((Int) -> Unit)? = null
    private var coverUrlProvider: ((Track, Boolean) -> String?)? = null
    private var lastClient: OkHttpClient? = null

    // --- Windowed queue loading -------------------------------------------------
    // Building a MediaItem per track resolves stream/cover URLs (disk I/O for the
    // offline library), so doing it for an entire library up front is what stalls
    // the UI thread on large collections. Instead we only ever build the item for
    // the track that's about to play synchronously, then fill a window of items
    // around it in the background, extending it further as playback approaches
    // either edge. `windowStart` is the index into the active track list (queue or
    // shuffledQueue) that corresponds to MediaItem index 0 in currentPlayer.
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadGeneration = 0
    private var windowStart = 0
    private var windowMaintenancePending = false

    private fun activeList(): List<Track> = if (shuffle) shuffledQueue else queue

    fun setOkHttpClient(client: OkHttpClient?) {
        if (client === lastClient && client != null) return
        lastClient = client

        val track = _currentTrack.value
        val wasPlaying = exoPlayer.isPlaying
        val position = exoPlayer.currentPosition
        val isExoCurrent = (currentPlayer === exoPlayer)

        detachFromSession()
        exoPlayer.removeListener(listener)
        exoPlayer.removeAnalyticsListener(analyticsListener)
        exoPlayer.release()
        exoPlayer = buildExoPlayer(client)
        exoPlayer.repeatMode = when (loopMode) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.setPlaybackSpeed(playbackSpeed)
        exoPlayer.addAnalyticsListener(analyticsListener)
        equalizerManager.onAudioSessionIdChanged(exoPlayer.audioSessionId)
        if (isExoCurrent) {
            currentPlayer = exoPlayer
            exoPlayer.addListener(listener)
            activePlayer = exoPlayer

            val provider = streamUrlProvider
            val list = activeList()
            if (track != null && provider != null && list.isNotEmpty()) {
                // Only rebuild a window around the current track, not the whole queue.
                val idx = if (shuffle) shuffleIndex else queueIndex
                loadWindowed(list, idx, provider, position, autoplay = wasPlaying)
            }
        }
        // If Cast is currently active, exoPlayer's queue is rebuilt fresh (windowed)
        // by switchToPlayer() whenever the cast session ends, so there's no need to
        // eagerly rebuild it here too.
        if (_currentTrack.value != null) {
            startPlaybackService()
        }
    }

    fun setPlaybackCallbacks(
        streamUrl: (Track, Boolean) -> String,
        onIncrementPlay: (Int) -> Unit,
        coverUrl: (Track, Boolean) -> String? = { t, fr -> null },
    ) {
        streamUrlProvider = streamUrl
        incrementPlayCallback = onIncrementPlay
        coverUrlProvider = coverUrl
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) {
                startPlaybackService()
            } else if (_currentTrack.value == null) {
                stopPlaybackService()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Keep our currentTrack / queueIndex in sync when ExoPlayer advances
            // the playlist natively (e.g. via notification next/prev buttons).
            // currentMediaItemIndex is local to the loaded window, so map it back
            // to a global index into queue/shuffledQueue via windowStart.
            val newIndex = windowStart + currentPlayer.currentMediaItemIndex
            val isRepeat = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

            if (shuffle) {
                // In shuffle mode ExoPlayer plays a flat shuffled list, so newIndex maps
                // directly into shuffledQueue.
                if ((newIndex != shuffleIndex || isRepeat) && newIndex in shuffledQueue.indices) {
                    shuffleIndex = newIndex
                    queueIndex = queue.indexOf(shuffledQueue[shuffleIndex]).coerceAtLeast(0)
                    _currentTrack.value = shuffledQueue[shuffleIndex]
                    incrementPlayCallback?.invoke(shuffledQueue[shuffleIndex].id)
                }
            } else {
                if ((newIndex != queueIndex || isRepeat) && newIndex in queue.indices) {
                    queueIndex = newIndex
                    _currentTrack.value = queue[queueIndex]
                    incrementPlayCallback?.invoke(queue[queueIndex].id)
                }
            }

            // Playback has moved — top up the loaded window in the background if we're
            // getting close to either edge of what's currently loaded into the player.
            scheduleWindowMaintenance()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                // ExoPlayer playlist exhausted – handled natively by repeatMode.
                // If repeatMode is OFF, we stop service.
                if (currentPlayer.repeatMode == Player.REPEAT_MODE_OFF) {
                    currentPlayer.pause()
                    _isPlaying.value = false
                    stopPlaybackService()
                }
            }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            equalizerManager.onAudioSessionIdChanged(audioSessionId)
        }
    }

    init {
        exoPlayer.addListener(listener)
        exoPlayer.addAnalyticsListener(analyticsListener)
        equalizerManager.onAudioSessionIdChanged(exoPlayer.audioSessionId)
        // Ensure shuffle is OFF by default on every app start
        shuffle = false
        currentPlayer.shuffleModeEnabled = false
        castPlayer // initialize
        attachToSession()
    }

    private fun buildExoPlayer(okHttp: OkHttpClient?): ExoPlayer {
        val builder = ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)

        if (okHttp != null) {
            val httpFactory = OkHttpDataSource.Factory(okHttp)
            val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        }
        return builder.build()
    }

    private fun buildMediaItem(track: Track, streamUrl: String, forceRemoteCover: Boolean = false): MediaItem {
        val cover = coverUrlProvider?.invoke(track, forceRemoteCover)
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setDisplayTitle(track.title)
            .setArtist(track.artist)
            .setSubtitle(track.artist)
            .setAlbumTitle(track.genre)
            .setStation("Amethyst Music")
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setExtras(Bundle().apply {
                putString("com.google.android.gms.cast.metadata.TITLE", "Amethyst Music")
            })
        if (!cover.isNullOrBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(cover))
        }
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType("audio/*")
            .setMediaId(track.id.toString())
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    // Loads just the MediaItem for `list[index]` into the player synchronously (so
    // playback/seeking starts instantly regardless of library size), then kicks off a
    // background fill of the surrounding window.
    private fun loadWindowed(
        list: List<Track>,
        index: Int,
        streamUrl: (Track, Boolean) -> String,
        startPositionMs: Long,
        autoplay: Boolean,
    ) {
        if (list.isEmpty()) return
        val safeIndex = index.coerceIn(0, list.lastIndex)
        val isCast = currentPlayer is CastPlayer

        loadGeneration++
        windowStart = safeIndex

        val centerItem = buildMediaItem(list[safeIndex], streamUrl(list[safeIndex], isCast), isCast)
        currentPlayer.setMediaItems(listOf(centerItem), 0, startPositionMs)
        currentPlayer.shuffleModeEnabled = false
        currentPlayer.prepare()
        if (autoplay) currentPlayer.play()

        _currentTrack.value = list[safeIndex]
        scheduleWindowMaintenance()
    }

    // Tops up the loaded window in the background when playback is getting close to
    // either edge of what's currently in the player, instead of ever building
    // MediaItems for the whole track list at once. Safe to call frequently — it's a
    // no-op unless we're actually near an edge, and only one fill runs at a time.
    private fun scheduleWindowMaintenance() {
        if (windowMaintenancePending) return
        val urlFor = streamUrlProvider ?: return
        val list = activeList()
        if (list.isEmpty()) return

        val count = currentPlayer.mediaItemCount
        if (count == 0) return
        val curIdx = currentPlayer.currentMediaItemIndex.coerceIn(0, count - 1)
        val loadedEnd = windowStart + count
        val remainingAhead = loadedEnd - (windowStart + curIdx) - 1
        val remainingBehind = curIdx

        val needAhead = remainingAhead < WINDOW_EXTEND_TRIGGER && loadedEnd < list.size
        val needBehind = remainingBehind < WINDOW_EXTEND_TRIGGER && windowStart > 0
        if (!needAhead && !needBehind) return

        val isCast = currentPlayer is CastPlayer
        val aheadFrom = loadedEnd
        val aheadTo = (loadedEnd + WINDOW_EXTEND_CHUNK).coerceAtMost(list.size)
        val behindTo = windowStart
        val behindFrom = (windowStart - WINDOW_EXTEND_CHUNK).coerceAtLeast(0)
        val generation = loadGeneration
        val capturedWindowStart = windowStart
        val capturedCount = count

        windowMaintenancePending = true
        playerScope.launch {
            try {
                val aheadItems = if (needAhead && aheadFrom < aheadTo) {
                    withContext(Dispatchers.IO) {
                        list.subList(aheadFrom, aheadTo).map { buildMediaItem(it, urlFor(it, isCast), isCast) }
                    }
                } else emptyList()
                val behindItems = if (needBehind && behindFrom < behindTo) {
                    withContext(Dispatchers.IO) {
                        list.subList(behindFrom, behindTo).map { buildMediaItem(it, urlFor(it, isCast), isCast) }
                    }
                } else emptyList()

                // If the queue was reset, or the window moved (e.g. rapid next()/previous()
                // fallback) while we were building items in the background, discard this
                // batch rather than risk inserting duplicate/misplaced tracks — the next
                // playback event will simply re-trigger a fresh, correct fill.
                if (generation != loadGeneration) return@launch
                if (windowStart != capturedWindowStart || currentPlayer.mediaItemCount != capturedCount) return@launch

                if (aheadItems.isNotEmpty()) {
                    currentPlayer.addMediaItems(currentPlayer.mediaItemCount, aheadItems)
                }
                if (behindItems.isNotEmpty()) {
                    currentPlayer.addMediaItems(0, behindItems)
                    windowStart = behindFrom
                }
                trimBehindIfNeeded()
            } finally {
                windowMaintenancePending = false
            }
        }
    }

    // Bounds memory during very long forward-listening sessions by dropping already-played
    // items far behind the current position, once there's more headroom than any reasonable
    // "previous" use needs.
    private fun trimBehindIfNeeded() {
        val excess = currentPlayer.currentMediaItemIndex - WINDOW_MAX_BEHIND
        if (excess > 0) {
            currentPlayer.removeMediaItems(0, excess)
            windowStart += excess
        }
    }

    private fun attachToSession() {
        activePlayer = currentPlayer
    }

    private fun detachFromSession() {
        if (activePlayer === currentPlayer) {
            activePlayer = null
        }
    }

    private fun startPlaybackService() {
        activePlayer = currentPlayer
        val intent = Intent(appContext, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_SYNC
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopPlaybackService() {
        appContext.stopService(Intent(appContext, MusicPlaybackService::class.java))
    }

    fun updateProgress() {
        _positionMs.value = currentPlayer.currentPosition.coerceAtLeast(0L)
        val duration = currentPlayer.duration
        if (duration > 0) {
            _durationMs.value = duration
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int, streamUrl: (Track, Boolean) -> String) {
        queue = tracks
        queueIndex = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        if (tracks.isEmpty()) return

        if (shuffle) {
            // Build a shuffled list starting from the chosen track so it plays first.
            // This just reorders Track references (cheap) — no MediaItems are built yet.
            val start = tracks[queueIndex]
            val rest = tracks.toMutableList().also { it.removeAt(queueIndex) }.shuffled()
            shuffledQueue = listOf(start) + rest
            shuffleIndex = 0
            _activeQueue.value = shuffledQueue
            loadWindowed(shuffledQueue, 0, streamUrl, 0L, autoplay = true)
        } else {
            shuffledQueue = emptyList()
            shuffleIndex = 0
            _activeQueue.value = tracks
            loadWindowed(tracks, queueIndex, streamUrl, 0L, autoplay = true)
        }

        _isPlaying.value = true
        startPlaybackService()
    }

    fun playTrackAt(index: Int, streamUrl: (Track, Boolean) -> String) {
        val list = activeList()
        if (list.isEmpty()) return
        val target = index.coerceIn(0, list.lastIndex)

        if (shuffle) {
            shuffleIndex = target
            queueIndex = queue.indexOf(shuffledQueue[shuffleIndex]).coerceAtLeast(0)
        } else {
            queueIndex = target
        }
        _currentTrack.value = list[target]

        val localIndex = target - windowStart
        if (localIndex in 0 until currentPlayer.mediaItemCount) {
            // Already loaded in the current window — just seek, no rebuild needed.
            currentPlayer.seekTo(localIndex, 0L)
            currentPlayer.play()
            _isPlaying.value = true
            startPlaybackService()
            scheduleWindowMaintenance()
        } else {
            // Jumping somewhere outside the loaded window — load a fresh window there.
            loadWindowed(list, target, streamUrl, 0L, autoplay = true)
            _isPlaying.value = true
            startPlaybackService()
        }
    }

    fun togglePlayPause() {
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
        } else {
            if (_currentTrack.value != null) {
                startPlaybackService()
            }
            currentPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        currentPlayer.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun next(streamUrl: (Track, Boolean) -> String) {
        val list = activeList()
        if (list.isEmpty()) return

        if (currentPlayer.hasNextMediaItem()) {
            currentPlayer.seekToNextMediaItem()
            currentPlayer.play()
            startPlaybackService()
            scheduleWindowMaintenance()
            return
        }

        // Loaded window hasn't caught up (e.g. rapid skipping) — append just the next
        // single track instead of falling back to rebuilding anything large.
        val nextGlobalIndex = windowStart + currentPlayer.mediaItemCount
        when {
            nextGlobalIndex < list.size -> {
                val isCast = currentPlayer is CastPlayer
                val item = buildMediaItem(list[nextGlobalIndex], streamUrl(list[nextGlobalIndex], isCast), isCast)
                currentPlayer.addMediaItem(item)
                currentPlayer.seekToNextMediaItem()
                currentPlayer.play()
                startPlaybackService()
                scheduleWindowMaintenance()
            }
            currentPlayer.repeatMode == Player.REPEAT_MODE_ALL -> {
                currentPlayer.seekTo(0, 0L)
                currentPlayer.play()
                startPlaybackService()
            }
            else -> {
                currentPlayer.pause()
                _isPlaying.value = false
            }
        }
    }

    fun previous(streamUrl: (Track, Boolean) -> String) {
        val list = activeList()
        if (list.isEmpty()) return
        if (currentPlayer.currentPosition > 3000) {
            seekTo(0)
            return
        }

        if (currentPlayer.hasPreviousMediaItem()) {
            currentPlayer.seekToPreviousMediaItem()
            currentPlayer.play()
            startPlaybackService()
            scheduleWindowMaintenance()
            return
        }

        val prevGlobalIndex = windowStart - 1
        if (prevGlobalIndex >= 0) {
            val isCast = currentPlayer is CastPlayer
            val item = buildMediaItem(list[prevGlobalIndex], streamUrl(list[prevGlobalIndex], isCast), isCast)
            currentPlayer.addMediaItem(0, item)
            windowStart = prevGlobalIndex
            currentPlayer.seekToPreviousMediaItem()
            currentPlayer.play()
            startPlaybackService()
            scheduleWindowMaintenance()
        } else {
            // At the very start with nothing loaded before it — wrap to the end.
            loadWindowed(list, list.lastIndex, streamUrl, 0L, autoplay = true)
            startPlaybackService()
        }
    }

    fun toggleLoop(): Int {
        loopMode = (loopMode + 1) % 3
        currentPlayer.repeatMode = when (loopMode) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        return loopMode
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        currentPlayer.setPlaybackSpeed(speed)
    }

    fun expandQueueForShuffle(allTracks: List<Track>) {
        val currentT = _currentTrack.value ?: return
        if (allTracks.isEmpty()) return
        queue = allTracks
        queueIndex = queue.indexOfFirst { it.id == currentT.id }.coerceAtLeast(0)
        setShuffle(true, forceReshuffle = true)
    }

    // Reorders the player's playlist around whatever is currently playing, without
    // touching that item's own MediaSource — avoids the audible cut/rebuffer that
    // setMediaItems() causes when it recreates the source for the playing track too.
    private fun reorderPlayerKeepingCurrent(before: List<MediaItem>, after: List<MediaItem>) {
        val count = currentPlayer.mediaItemCount
        if (count == 0) return
        val curIdx = currentPlayer.currentMediaItemIndex.coerceIn(0, count - 1)
        if (curIdx + 1 < count) currentPlayer.removeMediaItems(curIdx + 1, count)
        if (curIdx > 0) currentPlayer.removeMediaItems(0, curIdx)
        // Only the currently playing item remains now, at index 0.
        if (after.isNotEmpty()) currentPlayer.addMediaItems(1, after)
        if (before.isNotEmpty()) currentPlayer.addMediaItems(0, before)
    }

    // CastPlayer mutates its queue asynchronously against the receiver, so the
    // incremental remove/add sequence in reorderPlayerKeepingCurrent (which assumes
    // ExoPlayer's synchronous local timeline) drifts out of sync with the receiver's
    // real queue. Rebuild via setMediaItems() instead, matching switchToPlayer()'s
    // strategy — but windowed to just the starting track; scheduleWindowMaintenance()
    // fills the rest in the background via plain appends, which stay in sync fine.
    private fun rebuildQueueOnCast(tracks: List<Track>, startIndex: Int, urlFor: (Track, Boolean) -> String) {
        val wasPlaying = currentPlayer.isPlaying
        val position = currentPlayer.currentPosition.coerceAtLeast(0L)
        val item = buildMediaItem(tracks[startIndex], urlFor(tracks[startIndex], true), true)
        currentPlayer.setMediaItems(listOf(item), 0, position)
        currentPlayer.prepare()
        if (wasPlaying) currentPlayer.play()
    }

    fun setShuffle(enabled: Boolean, forceReshuffle: Boolean = false) {
        val wasEnabled = shuffle
        shuffle = enabled
        val isCast = currentPlayer is CastPlayer

        if (enabled) {
            if (!wasEnabled || forceReshuffle) {
                // Generate a fresh shuffled queue; keep current track first. This only
                // reorders Track references (cheap) — no MediaItems are built for the
                // whole library here.
                val currentT = _currentTrack.value
                if (queue.isNotEmpty() && currentT != null) {
                    val rest = queue.toMutableList().also { list ->
                        val idx = list.indexOfFirst { it.id == currentT.id }
                        if (idx >= 0) list.removeAt(idx)
                    }.shuffled()
                    shuffledQueue = listOf(currentT) + rest
                    shuffleIndex = 0
                    loadGeneration++
                    windowStart = 0

                    streamUrlProvider?.let { urlFor ->
                        if (isCast) {
                            rebuildQueueOnCast(shuffledQueue, 0, urlFor)
                        } else {
                            // Strip the player down to just the currently playing item
                            // (no MediaSource rebuild for it, so no audible cut) and let
                            // the background window maintenance fill in the rest.
                            reorderPlayerKeepingCurrent(before = emptyList(), after = emptyList())
                        }
                    }
                    _activeQueue.value = shuffledQueue
                    scheduleWindowMaintenance()
                }
            }
        } else {
            // Switching shuffle OFF: revert to the canonical queue at current track
            val currentT = _currentTrack.value
            if (queue.isNotEmpty() && currentT != null) {
                queueIndex = queue.indexOfFirst { it.id == currentT.id }.coerceAtLeast(0)
                shuffledQueue = emptyList()
                shuffleIndex = 0
                loadGeneration++
                windowStart = queueIndex

                streamUrlProvider?.let { urlFor ->
                    if (isCast) {
                        rebuildQueueOnCast(queue, queueIndex, urlFor)
                    } else {
                        reorderPlayerKeepingCurrent(before = emptyList(), after = emptyList())
                    }
                }
                _activeQueue.value = queue
                scheduleWindowMaintenance()
            }
        }

        // Always keep ExoPlayer's own shuffle OFF; we manage order ourselves
        currentPlayer.shuffleModeEnabled = false
    }

    fun reshuffle() {
        if (shuffle) setShuffle(enabled = true, forceReshuffle = true)
    }

    fun toggleShuffle(): Boolean {
        setShuffle(!shuffle, forceReshuffle = true)
        return shuffle
    }

    fun playbackState(): Int = currentPlayer.playbackState

    fun stop() {
        loadGeneration++
        windowStart = 0
        currentPlayer.stop()
        currentPlayer.clearMediaItems()
        _isPlaying.value = false
        _currentTrack.value = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        stopPlaybackService()
    }

    // Kept for compatibility; with the playlist approach this is handled by onPlaybackStateChanged
    fun handleTrackEnded(streamUrl: (Track) -> String, onIncrementPlay: (Int) -> Unit) {
        // no-op: ExoPlayer handles playlist advancement automatically
    }

    fun release() {
        stop()
        loadGeneration++
        playerScope.cancel()
        currentPlayer.removeListener(listener)
        exoPlayer.removeAnalyticsListener(analyticsListener)
        equalizerManager.release()
        detachFromSession()
        exoPlayer.release()
        castPlayer?.release()
    }

    companion object {
        @Volatile
        var activePlayer: Player? = null

        // How close (in loaded items) to either edge of the window triggers a background top-up.
        private const val WINDOW_EXTEND_TRIGGER = 8

        // How many more tracks to load per background top-up.
        private const val WINDOW_EXTEND_CHUNK = 40

        // How many already-played items to keep loaded behind the current position before trimming.
        private const val WINDOW_MAX_BEHIND = 200
    }
}
