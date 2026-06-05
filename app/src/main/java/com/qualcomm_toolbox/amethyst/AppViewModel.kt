package com.qualcomm_toolbox.amethyst

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    Setup,
    Login,
    Main,
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ServerPreferences(application)
    private val sessionPersistence = SessionPersistence(application)
    private val offlineLibrary = OfflineLibrary(application)
    private val trackDownloader = TrackDownloader()
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredTracks = MutableStateFlow<List<Track>>(emptyList())
    val filteredTracks: StateFlow<List<Track>> = _filteredTracks.asStateFlow()

    private val _filteredOfflineTracks = MutableStateFlow<List<Track>>(emptyList())
    val filteredOfflineTracks: StateFlow<List<Track>> = _filteredOfflineTracks.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _language = MutableStateFlow(prefs.language)
    val language: StateFlow<String> = _language.asStateFlow()

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
        client = PurpleClient(normalized, trustAllCerts, cookieJar)
        musicPlayer.setOkHttpClient(client?.okHttpClient)
    }

    private fun tryRestoreSession() {
        val purple = client ?: return
        val serverUrl = currentServerUrl() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val restored = withContext(Dispatchers.IO) {
                    if (purple.hasValidSession()) return@withContext true
                    val user = prefs.savedUsername
                    val pass = sessionPersistence.savedPassword
                    if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                        purple.login(user, pass)
                        return@withContext true
                    }
                    false
                }
                if (restored) {
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
        _offlineTracks.value = offlineLibrary.getTracks(server)
        _downloadedIds.value = offlineLibrary.getDownloadedIds(server).toSet()
        applyFilter()
        applyOfflineFilter()
    }

    fun playbackUrl(track: Track): String {
        val server = currentServerUrl()
        if (server != null) {
            offlineLibrary.getMusicUri(server, track.id)?.let { return it.toString() }
        }
        return client?.musicUrl(track.filename)
            ?: offlineLibrary.getMusicUri(server.orEmpty(), track.id)?.toString()
            ?: ""
    }

    fun coverUrlForTrack(track: Track): String? {
        val server = currentServerUrl()
        if (server != null) {
            offlineLibrary.getCoverUri(server, track.id)?.let { return it.toString() }
        }
        return client?.coverUrl(track.cover)
    }

    fun coverUrl(cover: String): String? = client?.coverUrl(cover)

    fun isDownloaded(trackId: Int): Boolean = _downloadedIds.value.contains(trackId)

    fun isDownloading(trackId: Int): Boolean = _downloadingIds.value.contains(trackId)

    fun clearError() {
        _error.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        // Debounced + background filtering
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(250)
            withContext(Dispatchers.Main) {
                applyFilter()
                applyOfflineFilter()
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setLanguage(lang: String) {
        prefs.language = lang
        _language.value = lang
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    fun refreshCache() {
        loadLibrary()
    }

    fun openFullPlayer() {
        _showFullPlayer.value = true
    }

    fun closeFullPlayer() {
        _showFullPlayer.value = false
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
                withContext(Dispatchers.IO) {
                    purple.login(username.trim(), password)
                }
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
                withContext(Dispatchers.IO) {
                    purple.register(username.trim(), password)
                    purple.login(username.trim(), password)
                }
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
                val (trackList, playlistList, genreList) = withContext(Dispatchers.IO) {
                    Triple(purple.fetchTracks(), purple.fetchPlaylists(), purple.fetchGenres())
                }
                _tracks.value = trackList
                _playlists.value = playlistList
                _genres.value = genreList
                refreshOfflineState()
                applyFilter()
            } catch (e: PurpleException) {
                _error.value = e.message
                if (e.message?.contains("Session") == true) {
                    purple.clearSession()
                    _screen.value = AppScreen.Login
                }
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
        sessionPersistence.clearCredentials()
        sessionPersistence.clearAllForServer(currentServerUrl())
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

    private fun applyFilter() {
        val q = _searchQuery.value.lowercase().trim()
        val list = if (q.isEmpty()) {
            _tracks.value
        } else {
            _tracks.value.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.genre.contains(q, ignoreCase = true)
            }
        }
        _filteredTracks.value = list
    }

    private fun applyOfflineFilter() {
        val q = _searchQuery.value.lowercase().trim()
        val list = if (q.isEmpty()) {
            _offlineTracks.value
        } else {
            _offlineTracks.value.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.genre.contains(q, ignoreCase = true)
            }
        }
        _filteredOfflineTracks.value = list
    }

    fun playTrack(track: Track) {
        val queue = when (_selectedTab.value) {
            2 -> _filteredOfflineTracks.value.ifEmpty { _offlineTracks.value }
            else -> _filteredTracks.value.ifEmpty { _tracks.value }
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
                    _error.value = "Cette playlist est vide."
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
                delay(1500) // 1.5 seconds when music is playing
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
