package com.qualcomm_toolbox.amethyst

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewModelScope
import com.qualcomm_toolbox.amethyst.data.LyricsCache
import com.qualcomm_toolbox.amethyst.data.OfflineLibrary
import com.qualcomm_toolbox.amethyst.data.PersistentCookieJar
import com.qualcomm_toolbox.amethyst.data.Playlist
import com.qualcomm_toolbox.amethyst.data.PurpleClient
import com.qualcomm_toolbox.amethyst.data.PurpleException
import com.qualcomm_toolbox.amethyst.data.ServerPreferences
import com.qualcomm_toolbox.amethyst.data.SessionPersistence
import com.qualcomm_toolbox.amethyst.data.Track
import com.qualcomm_toolbox.amethyst.data.TrackDownloader
import com.qualcomm_toolbox.amethyst.player.MusicPlayer
import com.qualcomm_toolbox.amethyst.player.PlaybackController
import com.qualcomm_toolbox.amethyst.player.PlaybackHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

enum class AppScreen {
    Setup,
    Login,
    Main,
}

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
    private val lyricsCache = LyricsCache(application)
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

    private val _language = MutableStateFlow(prefs.language)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _isAdmin = MutableStateFlow(prefs.isAdmin)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _adminModeEnabled = MutableStateFlow(prefs.adminModeEnabled)
    val adminModeEnabled: StateFlow<Boolean> = _adminModeEnabled.asStateFlow()

    private val _showFullPlayer = MutableStateFlow(false)
    val showFullPlayer: StateFlow<Boolean> = _showFullPlayer.asStateFlow()

    private val _trustAllCerts = MutableStateFlow(prefs.trustAllCertificates)
    val trustAllCerts: StateFlow<Boolean> = _trustAllCerts.asStateFlow()

    private val _downloadedIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadedIds: StateFlow<Set<Int>> = _downloadedIds.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingIds: StateFlow<Set<Int>> = _downloadingIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Float>> = _downloadProgress.asStateFlow()

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics.asStateFlow()

    data class LyricLine(val timeMs: Long, val text: String)
    private val _parsedLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val parsedLyrics: StateFlow<List<LyricLine>> = _parsedLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

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
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(prefs.language))
        prefs.serverUrl?.let { url ->
            initClient(url, prefs.trustAllCertificates)
            tryRestoreSession()
        }
        refreshOfflineState()
        startProgressUpdates()
        updatePlayerCallbacks()
        observeTrackChanges()
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
            streamUrl = { playbackUrl(it) },
            onIncrementPlay = { id ->
                if (!_offlineOnlyMode.value) {
                    client?.let { purple ->
                        viewModelScope.launch(Dispatchers.IO) { purple.incrementPlay(id) }
                    }
                }
            },
            coverUrl = { coverUrlForTrack(it) },
        )
    }

    private fun initClient(url: String, trustAllCerts: Boolean = prefs.trustAllCertificates) {
        val normalized = ServerPreferences.normalizeServerUrl(url)
        cookieJar = PersistentCookieJar(normalized, sessionPersistence)
        client = PurpleClient(normalized, trustAllCerts, cookieJar).apply {
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

    fun playbackUrl(track: Track): String {
        val server = currentServerUrl()
        if (server != null) {
            offlineLibrary.getMusicUri(server, track.id)?.let { return it.toString() }
        }
        return client?.musicUrl(track.id)
            ?: offlineLibrary.getMusicUri(server.orEmpty(), track.id)?.toString()
            ?: ""
    }

    fun coverUrlForTrack(track: Track): String? {
        val server = currentServerUrl()
        if (server != null) {
            offlineLibrary.getCoverUri(server, track.id)?.let { return it.toString() }
        }
        return client?.coverUrl(track.id)
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

    fun setAdminModeEnabled(enabled: Boolean) {
        prefs.adminModeEnabled = enabled
        _adminModeEnabled.value = enabled
    }

    fun refreshCache() {
        lyricsCache.clear()
        loadLibrary()
    }

    fun openFullPlayer() {
        _showFullPlayer.value = true
    }

    fun closeFullPlayer() {
        _showFullPlayer.value = false
        _showLyrics.value = false
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
        if (newState && _lyrics.value == null) {
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

    fun saveServer(url: String, trustAllCerts: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val normalized = ServerPreferences.normalizeServerUrl(url)
                prefs.trustAllCertificates = trustAllCerts
                _trustAllCerts.value = trustAllCerts
                prefs.serverUrl = normalized
                _serverUrl.value = normalized
                initClient(normalized, trustAllCerts)
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

    fun loadLibrary() {
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
                
                _genres.value = purple.fetchGenres()
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
        val server = currentServerUrl() ?: return
        if (isDownloaded(track.id) || isDownloading(track.id)) return

        viewModelScope.launch {
            _downloadingIds.update { it + track.id }
            _downloadProgress.update { it + (track.id to 0f) }
            try {
                withContext(Dispatchers.IO) {
                    trackDownloader.download(
                        httpClient = purple.okHttpClient,
                        purple = purple,
                        track = track,
                        serverUrl = server,
                        library = offlineLibrary,
                        onProgress = { progress ->
                            // Throttle progress updates
                            if (progress % 0.08f < 0.01f || progress > 0.95f) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    _downloadProgress.update { it + (track.id to progress) }
                                }
                            }
                        },
                    )
                }
                refreshOfflineState()
            } catch (e: PurpleException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = getString(R.string.error_download_failed, e.message ?: "")
            } finally {
                _downloadingIds.update { it - track.id }
                _downloadProgress.update { it - track.id }
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
        _screen.value = AppScreen.Login
    }

    fun playTrack(track: Track) {
        val queue = when (_selectedTab.value) {
            2 -> filteredOfflineTracks.value.ifEmpty { offlineTracks.value }
            else -> filteredTracks.value.ifEmpty { tracks.value }
        }
        if (queue.isEmpty()) return
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        musicPlayer.playQueue(queue, index) { playbackUrl(it) }
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
                musicPlayer.playQueue(tracks, 0) { playbackUrl(it) }
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

    fun togglePlayPause() = musicPlayer.togglePlayPause()

    fun nextTrack() {
        musicPlayer.next { playbackUrl(it) }
        if (!_offlineOnlyMode.value) {
            musicPlayer.currentTrack.value?.let { t ->
                client?.let { purple ->
                    viewModelScope.launch(Dispatchers.IO) { purple.incrementPlay(t.id) }
                }
            }
        }
    }

    fun previousTrack() {
        musicPlayer.previous { playbackUrl(it) }
    }

    fun seekTo(ms: Long) = musicPlayer.seekTo(ms)

    fun toggleLoop() = musicPlayer.toggleLoop()

    fun toggleShuffle() = musicPlayer.toggleShuffle()

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
        super.onCleared()
    }
}
