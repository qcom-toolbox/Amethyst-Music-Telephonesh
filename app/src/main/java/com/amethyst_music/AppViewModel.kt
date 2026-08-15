package com.amethyst_music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewModelScope
import com.amethyst_music.data.LyricsCache
import com.amethyst_music.data.OfflineLibrary
import com.amethyst_music.data.PersistentCookieJar
import com.amethyst_music.data.Playlist
import com.amethyst_music.data.PurpleClient
import com.amethyst_music.data.PurpleException
import com.amethyst_music.data.ServerPreferences
import com.amethyst_music.data.SessionPersistence
import com.amethyst_music.data.Track
import com.amethyst_music.data.TrackDownloader
import com.amethyst_music.player.MusicPlayer
import com.amethyst_music.player.PlaybackController
import com.amethyst_music.util.DownloadNotificationManager
import com.amethyst_music.util.NetworkObserver
import com.amethyst_music.util.NetworkStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.amethyst_music.player.PlaybackHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class AppScreen {
    Setup,
    Login,
    Main,
}

private data class DownloadNotifState(
    val active: Boolean,
    val total: Int,
    val completed: Int,
    val progressPercent: Int,
)

enum class SortOrder {
    POPULARITY,
    TITLE_ASC,
    ARTIST_ASC,
    DATE_UPLOAD_DESC,
    DATE_UPLOAD_ASC
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ServerPreferences(application)
    private val sessionPersistence = SessionPersistence(application)
    private val offlineLibrary = OfflineLibrary(application)
    private val trackDownloader = TrackDownloader()
    private val downloadNotificationManager = DownloadNotificationManager(application)
    private val lyricsCache = LyricsCache(application)
    private val networkObserver = NetworkObserver(application)
    private var cookieJar: PersistentCookieJar? = null
    private var client: PurpleClient? = null
    val musicPlayer = MusicPlayer(application)

    private val _screen = MutableStateFlow(
        when {
            !prefs.hasServer -> AppScreen.Setup
            else -> AppScreen.Login
        },
    )
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _offlineOnlyMode = MutableStateFlow(false)
    val offlineOnlyMode: StateFlow<Boolean> = _offlineOnlyMode.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _showNetworkMessage = Channel<String>(Channel.CONFLATED)
    val networkMessages = _showNetworkMessage.receiveAsFlow()

    private val _showOfflineConfirmation = MutableStateFlow(false)
    val showOfflineConfirmation: StateFlow<Boolean> = _showOfflineConfirmation.asStateFlow()

    private var hasDeclinedOfflineThisSession = false

    private val _serverUrl = MutableStateFlow(prefs.serverUrl.orEmpty())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _siteName = MutableStateFlow("Amethyst Music")
    val siteName: StateFlow<String> = _siteName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _offlineTracks = MutableStateFlow<List<Track>>(emptyList())
    val offlineTracks: StateFlow<List<Track>> = _offlineTracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _genres = MutableStateFlow<List<String>>(emptyList())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

    private val _homeRecommended = MutableStateFlow<List<Track>>(emptyList())
    val homeRecommendedTracks: StateFlow<List<Track>> = _homeRecommended.asStateFlow()

    private val _homePopular = MutableStateFlow<List<Track>>(emptyList())
    val homePopularTracks: StateFlow<List<Track>> = _homePopular.asStateFlow()

    private val _homeHiddenGems = MutableStateFlow<List<Track>>(emptyList())
    val homeHiddenGems: StateFlow<List<Track>> = _homeHiddenGems.asStateFlow()

    private val _selectedGenres = MutableStateFlow<Set<String>>(emptySet())
    val selectedGenres: StateFlow<Set<String>> = _selectedGenres.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.POPULARITY)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTracks: StateFlow<List<Track>> = combine(
        _tracks, _searchQuery, _selectedGenres, _sortOrder
    ) { tracks, query, genres, sort ->
        var filtered = tracks
        val q = query.lowercase().trim()
        if (q.isNotEmpty()) {
            filtered = filtered.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.genre.contains(q, ignoreCase = true)
            }
        }
        if (genres.isNotEmpty()) {
            filtered = filtered.filter { genres.contains(it.genre) }
        }
        when (sort) {
            SortOrder.POPULARITY -> filtered.sortedByDescending { it.playCount }
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.DATE_UPLOAD_DESC -> filtered.sortedByDescending { it.id }
            SortOrder.DATE_UPLOAD_ASC -> filtered.sortedBy { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredOfflineTracks: StateFlow<List<Track>> = combine(
        _offlineTracks, _searchQuery, _selectedGenres, _sortOrder
    ) { tracks, query, genres, sort ->
        var filtered = tracks
        val q = query.lowercase().trim()
        if (q.isNotEmpty()) {
            filtered = filtered.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.genre.contains(q, ignoreCase = true)
            }
        }
        if (genres.isNotEmpty()) {
            filtered = filtered.filter { genres.contains(it.genre) }
        }
        when (sort) {
            SortOrder.POPULARITY -> filtered.sortedByDescending { it.playCount }
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.DATE_UPLOAD_DESC -> filtered.sortedByDescending { it.id }
            SortOrder.DATE_UPLOAD_ASC -> filtered.sortedBy { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _recentGenres = MutableStateFlow(prefs.recentGenrePlays)
    
    private fun refreshHomeSections() {
        val allTracks = _tracks.value
        if (allTracks.isEmpty()) return

        _homePopular.value = allTracks.sortedByDescending { it.playCount }.take(10)
        
        // Hidden Gems: least listened songs (including 0 views) with random rotation
        // Take a pool of the top 30 lowest viewed tracks, then shuffle and take 10
        _homeHiddenGems.value = allTracks.sortedBy { it.playCount }.take(30).shuffled().take(10)
        
        val recent = _recentGenres.value
        if (recent.isEmpty()) {
            _homeRecommended.value = allTracks.shuffled().take(10)
        } else {
            val topGenres = recent.entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()
            val recommended = allTracks.filter { topGenres.contains(it.genre) }.shuffled().take(10)
            if (recommended.size < 5) {
                _homeRecommended.value = (recommended + allTracks.filter { !topGenres.contains(it.genre) }.shuffled().take(10 - recommended.size)).distinct()
            } else {
                _homeRecommended.value = recommended
            }
        }
    }

    private val _language = MutableStateFlow(prefs.language)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _backgroundColor = MutableStateFlow(prefs.backgroundColor)
    val backgroundColor: StateFlow<Long> = _backgroundColor.asStateFlow()

    private val _useHarmony = MutableStateFlow(prefs.useHarmony)
    val useHarmony: StateFlow<Boolean> = _useHarmony.asStateFlow()

    private val _isAdmin = MutableStateFlow(prefs.isAdmin)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _adminModeEnabled = MutableStateFlow(prefs.adminModeEnabled)
    val adminModeEnabled: StateFlow<Boolean> = _adminModeEnabled.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(prefs.defaultPlaybackSpeed)
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    private val _showFullPlayer = MutableStateFlow(false)
    val showFullPlayer: StateFlow<Boolean> = _showFullPlayer.asStateFlow()

    private val _downloadedIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadedIds: StateFlow<Set<Int>> = _downloadedIds.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingIds: StateFlow<Set<Int>> = _downloadingIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Float>> = _downloadProgress.asStateFlow()

    private val _isBulkDownloading = MutableStateFlow(false)
    val isBulkDownloading: StateFlow<Boolean> = _isBulkDownloading.asStateFlow()

    private val _isBulkDownloadPaused = MutableStateFlow(false)
    val isBulkDownloadPaused: StateFlow<Boolean> = _isBulkDownloadPaused.asStateFlow()

    private var bulkDownloadJob: Job? = null

    // Set while a bulk download is being torn down via cancelBulkDownload(), so the
    // in-flight download's forced IOException isn't reported to the user as a real failure.
    @Volatile private var isCancellingBulkDownload = false

    // Tracks progress through the current run of downloads, for the download notification.
    // Both reset to 0 once downloadingIds drains back to empty.
    private val _downloadQueueTotal = MutableStateFlow(0)
    private val _downloadQueueCompleted = MutableStateFlow(0)

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics.asStateFlow()

    // Cache for cover URLs to avoid repeated logic and blocking I/O
    private val coverUrlCache = ConcurrentHashMap<Pair<Int, Boolean>, String?>()

    data class LyricLine(val timeMs: Long, val text: String)
    private val _parsedLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val parsedLyrics: StateFlow<List<LyricLine>> = _parsedLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _showEqualizer = MutableStateFlow(false)
    val showEqualizer: StateFlow<Boolean> = _showEqualizer.asStateFlow()

    private val _showBulkDownload = MutableStateFlow(false)
    val showBulkDownload: StateFlow<Boolean> = _showBulkDownload.asStateFlow()

    private val _trackToAddToPlaylist = MutableStateFlow<Track?>(null)
    val trackToAddToPlaylist: StateFlow<Track?> = _trackToAddToPlaylist.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<Playlist?>(null)
    val currentPlaylist: StateFlow<Playlist?> = _currentPlaylist.asStateFlow()

    private val _currentPlaylistTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentPlaylistTracks: StateFlow<List<Track>> = _currentPlaylistTracks.asStateFlow()

    private var searchJob: Job? = null
    private var progressJob: Job? = null

    val hasOfflineLibrary: Boolean
        get() = offlineLibrary.hasTracksForServer(prefs.serverUrl)

    init {
        // Only override the per-app locale if the user explicitly picked one in Settings;
        // otherwise leave it unset so the app (and keyboard) follow the system locale.
        if (prefs.language.isNotBlank()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(prefs.language))
        }
        musicPlayer.setPlaybackSpeed(prefs.defaultPlaybackSpeed)
        prefs.serverUrl?.let { url ->
            initClient(url)
            tryRestoreSession()
        }
        refreshOfflineState()
        startProgressUpdates()
        updatePlayerCallbacks()
        observeTrackChanges()
        observeNetwork()
        observeDownloadNotifications()
    }

    private fun observeDownloadNotifications() {
        viewModelScope.launch {
            combine(
                _downloadingIds, _downloadProgress, _downloadQueueTotal, _downloadQueueCompleted,
            ) { downloading, progress, total, completed ->
                val avgPercent = if (progress.isNotEmpty()) {
                    (progress.values.average() * 100).toInt()
                } else {
                    0
                }
                DownloadNotifState(downloading.isNotEmpty(), total, completed, avgPercent)
            }.collectLatest { state ->
                if (!state.active) {
                    downloadNotificationManager.clear()
                } else {
                    downloadNotificationManager.update(
                        state.completed,
                        state.total.coerceAtLeast(1),
                        state.progressPercent,
                    )
                }
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkObserver.status.collectLatest { status ->
                val online = status == NetworkStatus.Available
                _isOnline.value = online
                
                if (!online) {
                    delay(3000) // Debounce network drops
                    if (_screen.value == AppScreen.Main && !_offlineOnlyMode.value && !hasDeclinedOfflineThisSession) {
                        // Only show popup if app is in foreground OR music is playing.
                        // If music is paused and phone is locked (background), don't trigger.
                        if (_isAppInForeground.value || musicPlayer.isPlaying.value) {
                            _showOfflineConfirmation.value = true
                        }
                    }
                }
            }
        }
    }

    /** Manually re-checks connectivity on demand (Settings button, pull-to-refresh in Offline tab). */
    fun recheckConnection() {
        if (_isCheckingConnection.value) return
        viewModelScope.launch {
            _isCheckingConnection.value = true
            val online = withContext(Dispatchers.IO) {
                networkObserver.currentStatus() == NetworkStatus.Available
            }
            _isOnline.value = online
            _showNetworkMessage.trySend(
                if (online) getString(R.string.connection_restored) else getString(R.string.still_offline)
            )
            _isCheckingConnection.value = false
        }
    }

    fun setAppInForeground(foreground: Boolean) {
        _isAppInForeground.value = foreground
        if (foreground && !_isOnline.value && _screen.value == AppScreen.Main && !_offlineOnlyMode.value && !hasDeclinedOfflineThisSession) {
            _showOfflineConfirmation.value = true
        }
    }

    fun confirmOfflineMode() {
        _showOfflineConfirmation.value = false
        _offlineOnlyMode.value = true
        _selectedTab.value = 3 // Switch to Downloads
    }

    fun dismissOfflineConfirmation() {
        _showOfflineConfirmation.value = false
        hasDeclinedOfflineThisSession = true
    }

    private fun observeTrackChanges() {
        viewModelScope.launch {
            musicPlayer.currentTrack.collectLatest { track ->
                _lyrics.value = null
                if (_showLyrics.value && track != null) {
                    fetchLyrics(track)
                }
            }
        }
    }

    fun okHttpClient() = client?.okHttpClient

    private fun getString(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    private fun currentServerUrl(): String? = prefs.serverUrl

    private fun updatePlayerCallbacks() {
        PlaybackHolder.controller = object : PlaybackController {
            override fun onSkipNext() = nextTrack()
            override fun onSkipPrevious() = previousTrack()
        }
        musicPlayer.setPlaybackCallbacks(
            streamUrl = { track, forceRemote -> playbackUrl(track, forceRemote) },
            onIncrementPlay = { id ->
                if (!_offlineOnlyMode.value) {
                    client?.let { purple ->
                        viewModelScope.launch(Dispatchers.IO) { purple.incrementPlay(id) }
                    }
                }
            },
            coverUrl = { track, forceRemote -> coverUrlForTrack(track, forceRemote) },
        )
    }

    private fun initClient(url: String) {
        val normalized = ServerPreferences.normalizeServerUrl(url)
        cookieJar = PersistentCookieJar(normalized, sessionPersistence)
        client = PurpleClient(normalized, cookieJar).apply {
            setCredentials(prefs.savedUsername, sessionPersistence.savedPassword)
        }
        musicPlayer.setOkHttpClient(client?.okHttpClient)
    }

    private fun tryRestoreSession() {
        val purple = client ?: return
        val serverUrl = currentServerUrl() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                var isAdminResult = prefs.isAdmin
                val restored = withContext(Dispatchers.IO) {
                    val user = prefs.savedUsername
                    val pass = sessionPersistence.savedPassword
                    
                    // If we have saved credentials, call login to get fresh is_admin status
                    if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                        isAdminResult = purple.login(user, pass)
                        return@withContext true
                    }
                    
                    // Otherwise try to use existing cookies
                    purple.hasValidSession()
                }
                
                if (restored) {
                    client?.setCredentials(prefs.savedUsername, sessionPersistence.savedPassword)
                    prefs.isAdmin = isAdminResult
                    _isAdmin.value = isAdminResult
                    _adminModeEnabled.value = prefs.adminModeEnabled
                    _offlineOnlyMode.value = false
                    loadLibrary()
                    _screen.value = AppScreen.Main
                }
            } catch (_: Exception) {
                purple.clearSession()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun persistLogin(username: String, password: String) {
        prefs.savedUsername = username
        sessionPersistence.saveCredentials(username, password)
    }

    private fun refreshOfflineState() {
        val server = currentServerUrl() ?: return
        offlineLibrary.reload()
        _offlineTracks.value = offlineLibrary.getTracks(server)
        _downloadedIds.value = offlineLibrary.getDownloadedIds(server).toSet()
    }

    fun playbackUrl(track: Track, forceRemote: Boolean = false): String {
        val server = currentServerUrl()
        if (server != null && !forceRemote) {
            offlineLibrary.getMusicUri(server, track.id)?.let { return it.toString() }
        }
        return client?.musicUrl(track.id) ?: ""
    }

    fun coverUrlForTrack(track: Track, forceRemote: Boolean = false): String? {
        val key = track.id to forceRemote
        coverUrlCache[key]?.let { return it }

        val server = currentServerUrl()
        val url = if (server != null && !forceRemote) {
            offlineLibrary.getCoverUri(server, track.id)?.toString()
                ?: client?.coverUrl(track.id)
        } else {
            client?.coverUrl(track.id)
        }
        
        if (url != null) coverUrlCache[key] = url
        return url
    }

    fun isDownloaded(trackId: Int): Boolean = _downloadedIds.value.contains(trackId)

    fun isDownloading(trackId: Int): Boolean = _downloadingIds.value.contains(trackId)

    fun clearError() {
        _error.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleGenre(genre: String) {
        _selectedGenres.update { current ->
            if (current.contains(genre)) current - genre
            else current + genre
        }
    }

    fun clearGenreFilters() {
        _selectedGenres.value = emptySet()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setLanguage(lang: String) {
        prefs.language = lang
        _language.value = lang
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    fun setBackgroundColor(color: Long) {
        prefs.backgroundColor = color
        _backgroundColor.value = color
    }

    fun setUseHarmony(enabled: Boolean) {
        prefs.useHarmony = enabled
        _useHarmony.value = enabled
    }

    fun setAdminModeEnabled(enabled: Boolean) {
        prefs.adminModeEnabled = enabled
        _adminModeEnabled.value = enabled
    }

    /** Sets the default playback speed applied at the start of every future session. */
    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.defaultPlaybackSpeed = speed
        _defaultPlaybackSpeed.value = speed
    }

    fun refreshCache() {
        lyricsCache.clear()
        coverUrlCache.clear()
        loadLibrary(refreshOfflineMetadata = true)
    }

    fun openFullPlayer() {
        _showFullPlayer.value = true
    }

    fun closeFullPlayer() {
        _showFullPlayer.value = false
        _showLyrics.value = false
    }

    fun openEqualizer() {
        _showEqualizer.value = true
    }

    fun closeEqualizer() {
        _showEqualizer.value = false
    }

    fun openBulkDownload() {
        _showBulkDownload.value = true
    }

    fun closeBulkDownload() {
        _showBulkDownload.value = false
    }

    fun showAddToPlaylist(track: Track) {
        _trackToAddToPlaylist.value = track
    }

    fun hideAddToPlaylist() {
        _trackToAddToPlaylist.value = null
    }

    fun toggleLyrics() {
        val newState = !_showLyrics.value
        _showLyrics.value = newState
        if (newState) {
            musicPlayer.currentTrack.value?.let { fetchLyrics(it) }
        }
    }

    private fun fetchLyrics(track: Track) {
        // Check cache first
        lyricsCache.get(track.id)?.let { cached ->
            _lyrics.value = cached
            _parsedLyrics.value = parseLrc(cached)
            _isLoadingLyrics.value = false
            return
        }

        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _lyrics.value = null
            try {
                val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                    .addQueryParameter("artist_name", track.artist)
                    .addQueryParameter("track_name", track.title)
                    .build()

                val request = Request.Builder()
                    .header("User-Agent", "AmethystMusic/1.0")
                    .url(url)
                    .build()
                val okHttpClient = client?.okHttpClient ?: OkHttpClient()

                withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val json = JSONObject(body)
                                val lrc = json.optString("syncedLyrics").ifBlank { json.optString("plainLyrics") }
                                val result = lrc.ifBlank { getString(R.string.no_lyrics) }
                                if (lrc.isNotBlank()) {
                                    lyricsCache.put(track.id, lrc)
                                }
                                _lyrics.value = result
                                _parsedLyrics.value = parseLrc(lrc)
                            }
                        } else if (response.code == 404) {
                            _lyrics.value = getString(R.string.no_lyrics)
                            _parsedLyrics.value = emptyList()
                        } else {
                            _lyrics.value = getString(R.string.error_lyrics)
                            _parsedLyrics.value = emptyList()
                        }
                    }
                }
            } catch (e: Exception) {
                _lyrics.value = getString(R.string.error_lyrics)
                _parsedLyrics.value = emptyList()
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        lrc.split("\n").forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val ms = match.groupValues[3].toLong().let { if (it < 100) it * 10 else it }
                val timeMs = (min * 60 + sec) * 1000 + ms
                val text = match.groupValues[4].trim()
                lines.add(LyricLine(timeMs, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun saveServer(url: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val normalized = ServerPreferences.normalizeServerUrl(url)
                prefs.serverUrl = normalized
                _serverUrl.value = normalized
                initClient(normalized)
                val purple = client!!
                val name = withContext(Dispatchers.IO) { purple.validateServer() }
                _siteName.value = name
                musicPlayer.setOkHttpClient(purple.okHttpClient)
                refreshOfflineState()
                updatePlayerCallbacks()
                _screen.value = AppScreen.Login
                onSuccess()
            } catch (e: PurpleException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = getString(
                    R.string.error_connection_failed,
                    e.message ?: getString(R.string.error_network)
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(username: String, password: String) {
        val purple = client ?: run {
            _error.value = getString(R.string.error_no_server)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val isAdmin = withContext(Dispatchers.IO) {
                    purple.login(username.trim(), password)
                }
                purple.setCredentials(username.trim(), password)
                prefs.isAdmin = isAdmin
                _isAdmin.value = isAdmin
                persistLogin(username.trim(), password)
                _offlineOnlyMode.value = false
                loadLibrary()
                _screen.value = AppScreen.Main
            } catch (e: PurpleException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = getString(
                    R.string.error_connection_failed,
                    e.message ?: getString(R.string.error_network)
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(username: String, password: String) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val isAdmin = withContext(Dispatchers.IO) {
                    purple.register(username.trim(), password)
                    purple.login(username.trim(), password)
                }
                purple.setCredentials(username.trim(), password)
                prefs.isAdmin = isAdmin
                _isAdmin.value = isAdmin
                persistLogin(username.trim(), password)
                _offlineOnlyMode.value = false
                loadLibrary()
                _screen.value = AppScreen.Main
            } catch (e: PurpleException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: getString(R.string.create_account_error)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openOfflineLibrary() {
        val server = currentServerUrl() ?: run {
            _error.value = getString(R.string.error_no_server)
            return
        }
        if (!offlineLibrary.hasTracksForServer(server)) {
            _error.value = getString(R.string.error_no_offline)
            return
        }
        _offlineOnlyMode.value = true
        refreshOfflineState()
        _selectedTab.value = 2
        _screen.value = AppScreen.Main
    }

    fun loadLibrary(refreshOfflineMetadata: Boolean = false) {
        if (_offlineOnlyMode.value) {
            refreshOfflineState()
            return
        }
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 1. Fetch tracks
                val trackList = withContext(Dispatchers.IO) {
                    purple.fetchTracks()
                }
                _tracks.value = trackList

                // 2. Fetch playlists in background
                launch {
                    try {
                        val playlists = withContext(Dispatchers.IO) { purple.fetchPlaylists() }
                        _playlists.value = playlists
                    } catch (_: Exception) {}
                }

                launch {
                    try {
                        val fetchedGenres = withContext(Dispatchers.IO) { purple.fetchGenres() }
                        _genres.value = fetchedGenres
                    } catch (_: Exception) {}
                }
                if (refreshOfflineMetadata) {
                    refreshDownloadedMetadata(trackList)
                }
                refreshHomeSections()
                refreshOfflineState()
            } catch (e: Exception) {
                _error.value = e.message ?: getString(R.string.error_load_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun downloadTrack(track: Track) {
        val purple = client ?: run {
            _error.value = getString(R.string.error_login_required_download)
            return
        }
        if (isDownloaded(track.id) || isDownloading(track.id)) return
        viewModelScope.launch { downloadTrackInternal(track, purple) }
    }

    fun downloadAllTracks() {
        val purple = client ?: run {
            _error.value = getString(R.string.error_login_required_download)
            return
        }
        if (_isBulkDownloading.value) return
        val toDownload = _tracks.value.filter { !isDownloaded(it.id) && !isDownloading(it.id) }
        if (toDownload.isEmpty()) return
        runBulkDownload(toDownload, purple)
    }

    fun refreshAllDownloads() {
        val purple = client ?: run {
            _error.value = getString(R.string.error_login_required_download)
            return
        }
        if (_isBulkDownloading.value) return
        val server = currentServerUrl() ?: return
        // Re-fetch the files using the already-cached metadata, so info shown for
        // downloaded tracks doesn't change here — only "Refresh Cache" in Settings does that.
        val tracksToRefresh = offlineLibrary.getTracks(server)
        if (tracksToRefresh.isEmpty()) return
        runBulkDownload(tracksToRefresh, purple, skipIfDownloaded = false)
    }

    private fun runBulkDownload(toDownload: List<Track>, purple: PurpleClient, skipIfDownloaded: Boolean = true) {
        _isBulkDownloadPaused.value = false
        bulkDownloadJob = viewModelScope.launch {
            _isBulkDownloading.value = true
            try {
                for (track in toDownload) {
                    while (_isBulkDownloadPaused.value && isActive) {
                        delay(300)
                    }
                    if (!isActive) break
                    val shouldSkip = isDownloading(track.id) || (skipIfDownloaded && isDownloaded(track.id))
                    if (!shouldSkip) {
                        downloadTrackInternal(track, purple)
                    }
                }
            } finally {
                isCancellingBulkDownload = false
                _isBulkDownloading.value = false
                _isBulkDownloadPaused.value = false
            }
        }
    }

    /** Toggles pausing the bulk download queue after the current track finishes. */
    fun toggleBulkDownloadPause() {
        if (!_isBulkDownloading.value) return
        _isBulkDownloadPaused.update { !it }
    }

    /**
     * Force-stops a stuck or unwanted bulk download without requiring an app restart:
     * cancels the queue, interrupts whichever file is downloading right now, and resets
     * all download-related state back to idle.
     */
    fun cancelBulkDownload() {
        if (!_isBulkDownloading.value && bulkDownloadJob == null) return
        isCancellingBulkDownload = true
        trackDownloader.cancelCurrent()
        bulkDownloadJob?.cancel()
        bulkDownloadJob = null
        _isBulkDownloading.value = false
        _isBulkDownloadPaused.value = false
        _downloadingIds.value = emptySet()
        _downloadProgress.value = emptyMap()
        _downloadQueueTotal.value = 0
        _downloadQueueCompleted.value = 0
        downloadNotificationManager.clear()
    }

    private suspend fun downloadTrackInternal(track: Track, purple: PurpleClient) {
        val server = currentServerUrl() ?: return
        _downloadingIds.update { it + track.id }
        _downloadProgress.update { it + (track.id to 0f) }
        _downloadQueueTotal.update { it + 1 }
        try {
            withContext(Dispatchers.IO) {
                trackDownloader.download(
                    httpClient = purple.okHttpClient,
                    purple = purple,
                    track = track,
                    serverUrl = server,
                    library = offlineLibrary,
                    onProgress = { progress ->
                        // TrackDownloader already throttles callbacks to ~every 200ms,
                        // so every update here is safe to forward directly.
                        viewModelScope.launch(Dispatchers.Main) {
                            _downloadProgress.update { it + (track.id to progress) }
                        }
                    },
                )
            }
            refreshOfflineState()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: PurpleException) {
            if (!isCancellingBulkDownload) _error.value = e.message
        } catch (e: Exception) {
            if (!isCancellingBulkDownload) _error.value = getString(R.string.error_download_failed, e.message ?: "")
        } finally {
            _downloadingIds.update { it - track.id }
            _downloadProgress.update { it - track.id }
            _downloadQueueCompleted.update { it + 1 }
            if (_downloadingIds.value.isEmpty()) {
                _downloadQueueTotal.value = 0
                _downloadQueueCompleted.value = 0
            }
        }
    }

    private suspend fun refreshDownloadedMetadata(freshTracks: List<Track>) {
        val server = currentServerUrl() ?: return
        val purple = client ?: return
        val downloadedIds = offlineLibrary.getDownloadedIds(server).toSet()
        if (downloadedIds.isEmpty()) return
        val tracksToRefresh = freshTracks.filter { downloadedIds.contains(it.id) }
        if (tracksToRefresh.isEmpty()) return

        withContext(Dispatchers.IO) {
            tracksToRefresh.forEach { track ->
                try {
                    val coverFile = offlineLibrary.coverFileFor(track.id, track.cover)
                    trackDownloader.downloadCover(purple.okHttpClient, purple.coverUrl(track.id), coverFile)
                    val coverRelPath = if (coverFile.exists() && coverFile.length() > 0) {
                        "covers/${track.id}.${track.cover.substringAfterLast('.', "png")}"
                    } else {
                        null
                    }
                    offlineLibrary.updateTrackMetadata(server, track, coverRelPath)
                } catch (_: Exception) {
                    offlineLibrary.updateTrackMetadata(server, track)
                }
            }
        }
    }

    fun uploadTrack(
        title: String,
        artist: String,
        genre: String,
        musicBytes: ByteArray,
        musicName: String,
        coverBytes: ByteArray?,
        coverName: String?,
        onSuccess: () -> Unit = {}
    ) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    purple.uploadTrack(
                        title = title,
                        artist = artist,
                        genre = genre,
                        musicBytes = musicBytes,
                        musicName = musicName,
                        coverBytes = coverBytes,
                        coverName = coverName
                    )
                }
                loadLibrary()
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message ?: "Upload failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeDownload(track: Track) {
        val server = currentServerUrl() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                offlineLibrary.remove(server, track.id)
            }
            refreshOfflineState()
        }
    }

    fun logout() {
        client?.clearSession()
        client?.setCredentials(null, null)
        prefs.isAdmin = false
        prefs.adminModeEnabled = false
        _isAdmin.value = false
        _adminModeEnabled.value = false
        sessionPersistence.clearCredentials()
        sessionPersistence.clearAllForServer(currentServerUrl())
        lyricsCache.clear()
        coverUrlCache.clear()
        musicPlayer.stop()
        _tracks.value = emptyList()
        _playlists.value = emptyList()
        _offlineOnlyMode.value = false
        _screen.value = AppScreen.Login
    }

    fun changeServer() {
        client?.clearSession()
        sessionPersistence.clearAllForServer(currentServerUrl())
        musicPlayer.stop()
        _offlineOnlyMode.value = false
        _screen.value = AppScreen.Setup
    }

    fun exitOfflineMode() {
        _offlineOnlyMode.value = false
        // Don't change screen, just load library if possible
        loadLibrary()
    }

    fun playTrack(track: Track) {
        val currentTab = _selectedTab.value
        val baseQueue = when (currentTab) {
            0 -> _tracks.value.shuffled() // Home: always random, changes each click
            3 -> filteredOfflineTracks.value.ifEmpty { offlineTracks.value }
            else -> filteredTracks.value.ifEmpty { tracks.value }
        }
        if (baseQueue.isEmpty()) return
        val index = baseQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        musicPlayer.playQueue(baseQueue, index) { t, fr -> playbackUrl(t, fr) }
        prefs.recordGenrePlay(track.genre)
        _recentGenres.value = prefs.recentGenrePlays

        if (!_offlineOnlyMode.value) {
            client?.let { purple ->
                viewModelScope.launch(Dispatchers.IO) { purple.incrementPlay(track.id) }
            }
        }
    }

    fun playPlaylist(playlist: Playlist) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val tracks = withContext(Dispatchers.IO) {
                    purple.fetchPlaylistTracks(playlist.songIds)
                }
                if (tracks.isEmpty()) {
                    _error.value = getString(R.string.empty_playlist)
                    return@launch
                }
                musicPlayer.playQueue(tracks, 0) { t, fr -> playbackUrl(t, fr) }
                withContext(Dispatchers.IO) { purple.incrementPlay(tracks.first().id) }
                openFullPlayer()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openPlaylist(playlist: Playlist) {
        _currentPlaylist.value = playlist
        _currentPlaylistTracks.value = emptyList()
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val tracks = withContext(Dispatchers.IO) {
                    purple.fetchPlaylistTracks(playlist.songIds)
                }
                _currentPlaylistTracks.value = tracks
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun closePlaylist() {
        _currentPlaylist.value = null
        _currentPlaylistTracks.value = emptyList()
    }

    fun createPlaylist(name: String) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    purple.createPlaylist(name)
                }
                loadLibrary()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addToPlaylist(playlist: Playlist, track: Track) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    purple.addToPlaylist(playlist.id, track.id)
                }
                loadLibrary()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeFromPlaylist(playlist: Playlist, track: Track) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    purple.removeFromPlaylist(playlist.id, track.id)
                }
                // Refresh current playlist view if we are viewing it
                if (_currentPlaylist.value?.id == playlist.id) {
                    openPlaylist(playlist)
                }
                loadLibrary()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    purple.deletePlaylist(playlist.id)
                }
                loadLibrary()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun editTrack(
        trackId: Int,
        title: String,
        artist: String,
        genre: String,
        newCover: ByteArray?,
        newCoverName: String?
    ) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    purple.editTrack(trackId, title, artist, genre, newCover, newCoverName)
                }
                loadLibrary()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTrack(trackId: Int) {
        val purple = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    purple.deleteTrack(trackId)
                }
                loadLibrary()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playTrackAt(index: Int) {
        musicPlayer.playTrackAt(index) { t, fr -> playbackUrl(t, fr) }
    }

    // The ordered list currently loaded into the player (shuffled or not)
    val activeQueue get() = musicPlayer.activeQueueFlow

    fun togglePlayPause() = musicPlayer.togglePlayPause()

    fun nextTrack() {
        musicPlayer.next { t, fr -> playbackUrl(t, fr) }
        if (!_offlineOnlyMode.value) {
            musicPlayer.currentTrack.value?.let { t ->
                client?.let { purple ->
                    viewModelScope.launch(Dispatchers.IO) { purple.incrementPlay(t.id) }
                }
            }
        }
    }

    fun previousTrack() {
        musicPlayer.previous { t, fr -> playbackUrl(t, fr) }
    }

    fun seekTo(ms: Long) = musicPlayer.seekTo(ms)

    fun toggleLoop() = musicPlayer.toggleLoop()

    /** Sets the playback speed for the current session only. */
    fun setPlaybackSpeed(speed: Float) = musicPlayer.setPlaybackSpeed(speed)

    fun toggleShuffle() {
        val currentlyShuffled = musicPlayer.shuffle
        if (!currentlyShuffled) {
            // Turning shuffle ON: expand the queue to ALL server tracks,
            // keeping the current track playing from its current position.
            val allTracks = if (_offlineOnlyMode.value) {
                offlineTracks.value
            } else {
                _tracks.value
            }
            if (allTracks.isNotEmpty() && musicPlayer.currentTrack.value != null) {
                musicPlayer.expandQueueForShuffle(allTracks)
                return
            }
        }
        musicPlayer.toggleShuffle()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                if (musicPlayer.isPlaying.value) {
                    musicPlayer.updateProgress()
                }
                delay(250) // 250ms for smoother UI updates
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        progressJob?.cancel()
        musicPlayer.release()
        downloadNotificationManager.clear()
        super.onCleared()
    }
}
