package com.qualcomm_toolbox.amethyst.player

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
import com.qualcomm_toolbox.amethyst.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val index = currentPlayer.currentMediaItemIndex
        val wasPlaying = currentPlayer.isPlaying
        val currentRepeatMode = currentPlayer.repeatMode
        val isCast = newPlayer is CastPlayer

        currentPlayer.removeListener(listener)
        currentPlayer.pause()

        currentPlayer = newPlayer
        currentPlayer.addListener(listener)

        // Rebuild media items for the new player (especially important for Cast)
        val items = if (streamUrlProvider != null) {
            val tracksToUse = if (shuffle) shuffledQueue else queue
            tracksToUse.map { buildMediaItem(it, streamUrlProvider!!(it, isCast), isCast) }
        } else {
            (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it) }
        }

        // Ensure we are within bounds
        val safeIndex = if (items.isNotEmpty()) index.coerceIn(0, items.size - 1) else 0

        currentPlayer.setMediaItems(items, safeIndex, position)
        currentPlayer.repeatMode = currentRepeatMode
        currentPlayer.prepare()
        if (wasPlaying) {
            currentPlayer.play()
        }

        // Sync local state flows immediately
        _isPlaying.value = wasPlaying
        _positionMs.value = position
        
        // Explicitly update current track to avoid desync
        val activeQueue = if (shuffle) shuffledQueue else queue
        if (safeIndex in activeQueue.indices) {
            if (shuffle) {
                shuffleIndex = safeIndex
                queueIndex = queue.indexOf(shuffledQueue[shuffleIndex]).coerceAtLeast(0)
                _currentTrack.value = shuffledQueue[shuffleIndex]
            } else {
                queueIndex = safeIndex
                _currentTrack.value = queue[queueIndex]
            }
        }

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

    private var streamUrlProvider: ((Track, Boolean) -> String)? = null
    private var incrementPlayCallback: ((Int) -> Unit)? = null
    private var coverUrlProvider: ((Track, Boolean) -> String?)? = null
    private var lastClient: OkHttpClient? = null

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
        exoPlayer.addAnalyticsListener(analyticsListener)
        equalizerManager.onAudioSessionIdChanged(exoPlayer.audioSessionId)
        if (isExoCurrent) {
            currentPlayer = exoPlayer
            exoPlayer.addListener(listener)
            activePlayer = exoPlayer
        }
        if (track != null && streamUrlProvider != null) {
            // Restore full queue into the new player
            val items = queue.map { buildMediaItem(it, streamUrlProvider!!(it, false), false) }
            exoPlayer.setMediaItems(items, queueIndex, position)
            exoPlayer.prepare()
            if (wasPlaying && isExoCurrent) exoPlayer.play()
        }
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
            val newIndex = currentPlayer.currentMediaItemIndex
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

        val isCast = currentPlayer is CastPlayer

        if (shuffle) {
            // Build a shuffled list starting from the chosen track so it plays first
            val start = tracks[queueIndex]
            val rest = tracks.toMutableList().also { it.removeAt(queueIndex) }.shuffled()
            shuffledQueue = listOf(start) + rest
            shuffleIndex = 0

            val mediaItems = shuffledQueue.map { buildMediaItem(it, streamUrl(it, isCast), isCast) }
            currentPlayer.setMediaItems(mediaItems, 0, 0L)
        } else {
            shuffledQueue = emptyList()
            shuffleIndex = 0
            val mediaItems = tracks.map { buildMediaItem(it, streamUrl(it, isCast), isCast) }
            currentPlayer.setMediaItems(mediaItems, queueIndex, 0L)
        }

        // Always keep ExoPlayer shuffle OFF – we manage the order ourselves
        currentPlayer.shuffleModeEnabled = false

        currentPlayer.prepare()
        currentPlayer.play()

        _currentTrack.value = if (shuffle) shuffledQueue[0] else tracks[queueIndex]
        _isPlaying.value = true
        _activeQueue.value = if (shuffle) shuffledQueue else tracks
        startPlaybackService()
    }

    fun playTrackAt(index: Int, streamUrl: (Track, Boolean) -> String) {
        if (queue.isEmpty()) return

        val isCast = currentPlayer is CastPlayer

        if (shuffle && shuffledQueue.isNotEmpty()) {
            // index refers to position in shuffledQueue
            shuffleIndex = index.coerceIn(0, shuffledQueue.lastIndex)
            queueIndex = queue.indexOf(shuffledQueue[shuffleIndex]).coerceAtLeast(0)
            _currentTrack.value = shuffledQueue[shuffleIndex]

            if (currentPlayer.mediaItemCount == shuffledQueue.size) {
                currentPlayer.seekTo(shuffleIndex, 0L)
            } else {
                val mediaItems = shuffledQueue.map { buildMediaItem(it, streamUrl(it, isCast), isCast) }
                currentPlayer.setMediaItems(mediaItems, shuffleIndex, 0L)
                currentPlayer.prepare()
            }
        } else {
            queueIndex = index.coerceIn(0, queue.lastIndex)
            _currentTrack.value = queue[queueIndex]

            if (currentPlayer.mediaItemCount == queue.size) {
                currentPlayer.seekTo(queueIndex, 0L)
            } else {
                val mediaItems = queue.map { buildMediaItem(it, streamUrl(it, isCast), isCast) }
                currentPlayer.setMediaItems(mediaItems, queueIndex, 0L)
                currentPlayer.prepare()
            }
        }
        currentPlayer.play()
        _isPlaying.value = true
        startPlaybackService()
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
        if (queue.isEmpty()) return
        if (currentPlayer.hasNextMediaItem()) {
            currentPlayer.seekToNextMediaItem()
            currentPlayer.play()
            startPlaybackService()
        } else if (currentPlayer.repeatMode == Player.REPEAT_MODE_ALL) {
            currentPlayer.seekTo(0, 0L)
            currentPlayer.play()
            startPlaybackService()
        } else {
            currentPlayer.pause()
            _isPlaying.value = false
        }
    }

    fun previous(streamUrl: (Track, Boolean) -> String) {
        if (queue.isEmpty()) return
        if (currentPlayer.currentPosition > 3000) {
            seekTo(0)
            return
        }
        if (currentPlayer.hasPreviousMediaItem()) {
            currentPlayer.seekToPreviousMediaItem()
        } else {
            currentPlayer.seekTo(queue.lastIndex, 0L)
        }
        currentPlayer.play()
        startPlaybackService()
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

    fun setShuffle(enabled: Boolean, forceReshuffle: Boolean = false) {
        val wasEnabled = shuffle
        shuffle = enabled

        if (enabled) {
            if (!wasEnabled || forceReshuffle) {
                // Generate a fresh shuffled queue; keep current track first
                val currentT = _currentTrack.value
                if (queue.isNotEmpty() && currentT != null) {
                    val rest = queue.toMutableList().also { list ->
                        val idx = list.indexOfFirst { it.id == currentT.id }
                        if (idx >= 0) list.removeAt(idx)
                    }.shuffled()
                    shuffledQueue = listOf(currentT) + rest
                    shuffleIndex = 0

                    // Reload ExoPlayer with the new shuffled order
                    streamUrlProvider?.let { urlFor ->
                        val isCast = currentPlayer is CastPlayer
                        val mediaItems = shuffledQueue.map { buildMediaItem(it, urlFor(it, isCast), isCast) }
                        val wasPlaying = currentPlayer.isPlaying
                        currentPlayer.setMediaItems(mediaItems, 0, currentPlayer.currentPosition)
                        currentPlayer.prepare()
                        if (wasPlaying) currentPlayer.play()
                    }
                    _activeQueue.value = shuffledQueue
                }
            }
        } else {
            // Switching shuffle OFF: revert to the canonical queue at current track
            val currentT = _currentTrack.value
            if (queue.isNotEmpty() && currentT != null) {
                queueIndex = queue.indexOfFirst { it.id == currentT.id }.coerceAtLeast(0)
                shuffledQueue = emptyList()
                shuffleIndex = 0

                streamUrlProvider?.let { urlFor ->
                    val isCast = currentPlayer is CastPlayer
                    val mediaItems = queue.map { buildMediaItem(it, urlFor(it, isCast), isCast) }
                    val wasPlaying = currentPlayer.isPlaying
                    currentPlayer.setMediaItems(mediaItems, queueIndex, currentPlayer.currentPosition)
                    currentPlayer.prepare()
                    if (wasPlaying) currentPlayer.play()
                }
                _activeQueue.value = queue
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
    }
}
