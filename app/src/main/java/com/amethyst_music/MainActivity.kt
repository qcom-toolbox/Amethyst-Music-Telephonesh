package com.amethyst_music

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import coil.compose.LocalImageLoader
import com.amethyst_music.data.ServerPreferences
import com.amethyst_music.ui.components.AddSongsToPlaylistDialog
import com.amethyst_music.ui.components.AddToPlaylistDialog
import com.amethyst_music.ui.screens.AlbumScreen
import com.amethyst_music.ui.screens.ArtistScreen
import com.amethyst_music.ui.screens.BulkDownloadScreen
import com.amethyst_music.ui.screens.EqualizerScreen
import com.amethyst_music.ui.screens.FullPlayerScreen
import com.amethyst_music.ui.screens.HistoryScreen
import com.amethyst_music.ui.screens.LoginScreen
import com.amethyst_music.ui.screens.MainScreen
import com.amethyst_music.ui.screens.PlaylistScreen
import com.amethyst_music.ui.screens.ServerSetupScreen
import com.amethyst_music.ui.theme.AmethystMusicTheme
import com.amethyst_music.ui.theme.ThemeUtils
import com.amethyst_music.util.NotificationPermissionHelper
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    private lateinit var notificationPermission: NotificationPermissionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPermission = NotificationPermissionHelper(this)
        notificationPermission.requestIfNeeded()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            val vm: AppViewModel = viewModel()
            val backgroundColor by vm.backgroundColor.collectAsState()
            val useHarmony by vm.useHarmony.collectAsState()
            val dynamicThemeEnabled by vm.dynamicThemeEnabled.collectAsState()
            val dynamicThemeFullPlayerOnly by vm.dynamicThemeFullPlayerOnly.collectAsState()
            val dynamicAlbumColor by vm.dynamicAlbumColor.collectAsState()

            // The color actually rendered app-wide: the extracted album art color when the
            // "Dynamic" theme is selected, otherwise the persisted preset color. The
            // full-screen-player-only toggle is purely additive (it can turn on album-art
            // coloring in the player even when a different theme is active) and must never
            // suppress the app-wide "Dynamic" theme.
            val effectiveBackgroundColor = if (dynamicThemeEnabled && dynamicAlbumColor != null) {
                dynamicAlbumColor!!
            } else {
                backgroundColor
            }

            androidx.compose.runtime.LaunchedEffect(effectiveBackgroundColor) {
                val colorInt = effectiveBackgroundColor.toInt()
                val isLight = ThemeUtils.isLight(ComposeColor(effectiveBackgroundColor))
                enableEdgeToEdge(
                    statusBarStyle = if (isLight) SystemBarStyle.light(colorInt, colorInt) else SystemBarStyle.dark(colorInt),
                    navigationBarStyle = if (isLight) SystemBarStyle.light(colorInt, colorInt) else SystemBarStyle.dark(colorInt),
                )
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                }
            }

            // Smoothly cross-fade the theme's base color instead of snapping when the
            // dynamic (album-art) color changes between tracks.
            val animatedBackgroundColor by animateColorAsState(
                targetValue = ComposeColor(effectiveBackgroundColor),
                animationSpec = tween(durationMillis = 700),
                label = "themeBackgroundColor",
            )

            AmethystMusicTheme(
                backgroundColor = animatedBackgroundColor,
                useHarmony = useHarmony
            ) {
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> vm.setAppInForeground(true)
                            Lifecycle.Event.ON_PAUSE -> vm.setAppInForeground(false)
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val screen by vm.screen.collectAsState()
                val isLoading by vm.isLoading.collectAsState()
                val error by vm.error.collectAsState()
                val siteName by vm.siteName.collectAsState()
                val searchQuery by vm.searchQuery.collectAsState()
                val filteredTracks by vm.filteredTracks.collectAsState()
                val filteredOfflineTracks by vm.filteredOfflineTracks.collectAsState()
                val playlists by vm.playlists.collectAsState()
                val selectedTab by vm.selectedTab.collectAsState()
                val useHarmony by vm.useHarmony.collectAsState()
                val showFullPlayer by vm.showFullPlayer.collectAsState()
                val offlineOnlyMode by vm.offlineOnlyMode.collectAsState()
                val currentTrack by vm.musicPlayer.currentTrack.collectAsState()
                val isPlaying by vm.musicPlayer.isPlaying.collectAsState()
                val artistLinksEnabled by vm.artistLinksEnabled.collectAsState()
                val currentLanguage by vm.language.collectAsState()
                val onArtistClick = remember(vm, artistLinksEnabled) {
                    { name: String -> if (artistLinksEnabled) vm.openArtistPage(name) }
                }

                val context = LocalContext.current
                val imageLoader = remember(vm.okHttpClient()) {
                    ImageLoader.Builder(context)
                        .okHttpClient(vm.okHttpClient() ?: OkHttpClient())
                        .crossfade(300)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .respectCacheHeaders(false)
                        .memoryCache {
                            MemoryCache.Builder(context)
                                .maxSizePercent(0.25) // 25% de la mémoire disponible
                                .build()
                        }
                        .build()
                }

                val prefs = remember { ServerPreferences(context) }

                CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = animatedBackgroundColor,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (screen) {
                                AppScreen.Setup -> ServerSetupScreen(
                                    isLoading = isLoading,
                                    error = error,
                                    onConnect = { url ->
                                        vm.clearError()
                                        vm.saveServer(url)
                                    },
                                )
                                AppScreen.Login -> LoginScreen(
                                    siteName = siteName,
                                    savedUsername = prefs.savedUsername,
                                    isLoading = isLoading,
                                    error = error,
                                    hasOfflineLibrary = vm.hasOfflineLibrary,
                                    language = currentLanguage,
                                    onLogin = { u, p ->
                                        vm.clearError()
                                        vm.login(u, p)
                                    },
                                    onRegister = { u, p ->
                                        vm.clearError()
                                        vm.register(u, p)
                                    },
                                    onOpenOffline = {
                                        vm.clearError()
                                        vm.openOfflineLibrary()
                                    },
                                    onChangeServer = { vm.changeServer() },
                                )
                                AppScreen.Main -> {
                                    val homeRecommended by vm.homeRecommendedTracks.collectAsState()
                                    val homePopular by vm.homePopularTracks.collectAsState()
                                    val homeHiddenGems by vm.homeHiddenGems.collectAsState()

                                MainScreen(
                                        vm = vm,
                                        siteName = siteName,
                                        selectedTab = selectedTab,
                                        searchQuery = searchQuery,
                                        tracks = filteredTracks,
                                        offlineTracks = filteredOfflineTracks,
                                        playlists = playlists,
                                        isLoading = isLoading,
                                        offlineOnlyMode = offlineOnlyMode,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        coverUrlForTrack = remember(vm) { { vm.coverUrlForTrack(it) } },
                                        onTabSelected = remember(vm) { { vm.setSelectedTab(it) } },
                                        onSearchChange = remember(vm) { { vm.setSearchQuery(it) } },
                                        onTrackClick = remember(vm) {
                                            { track ->
                                                notificationPermission.requestIfNeeded()
                                                vm.playTrack(track)
                                            }
                                        },
                                        onPlaylistClick = remember(vm) {
                                            { playlist -> vm.openPlaylist(playlist) }
                                        },
                                        onDownload = remember(vm) { { vm.downloadTrack(it) } },
                                        onRemoveDownload = remember(vm) { { vm.removeDownload(it) } },
                                        onRefresh = remember(vm) { { vm.loadLibrary() } },
                                        onLogout = remember(vm) { { vm.logout() } },
                                        onExitOffline = remember(vm) { { vm.exitOfflineMode() } },
                                        onMiniPlayerClick = remember(vm) { { vm.openFullPlayer() } },
                                        onTogglePlay = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.togglePlayPause()
                                            }
                                        },
                                        onNextTrack = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.nextTrack()
                                            }
                                        },
                                        onPreviousTrack = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.previousTrack()
                                            }
                                        },
                                        onUploadTrack = remember(vm) {
                                            { t, a, g, al, m, mn, c, cn ->
                                                vm.uploadTrack(t, a, g, al, m, mn, c, cn)
                                            }
                                        },
                                        homeRecommended = homeRecommended,
                                        homePopular = homePopular,
                                        homeHiddenGems = homeHiddenGems,
                                        backgroundColor = backgroundColor,
                                        useHarmony = useHarmony,
                                        onThemeChange = remember(vm) {
                                            { color, harmony, isDynamic ->
                                                vm.setBackgroundColor(color)
                                                vm.setUseHarmony(harmony)
                                                vm.setDynamicThemeEnabled(isDynamic)
                                            }
                                        },
                                        onArtistClick = onArtistClick,
                                        artistClickEnabled = artistLinksEnabled,
                                    )
                                }
                            }

                            val trackToAddToPlaylist by vm.trackToAddToPlaylist.collectAsState()
                            if (trackToAddToPlaylist != null) {
                                AddToPlaylistDialog(
                                    playlists = playlists,
                                    onDismiss = { vm.hideAddToPlaylist() },
                                    onPlaylistSelected = { playlist ->
                                        vm.addToPlaylist(playlist, trackToAddToPlaylist!!)
                                        vm.hideAddToPlaylist()
                                    }
                                )
                            }

                            val showEqualizer by vm.showEqualizer.collectAsState()
                            AnimatedVisibility(
                                visible = showEqualizer,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it })
                            ) {
                                EqualizerScreen(
                                    manager = vm.musicPlayer.equalizerManager,
                                    onClose = vm::closeEqualizer
                                )
                            }

                            val showBulkDownload by vm.showBulkDownload.collectAsState()
                            AnimatedVisibility(
                                visible = showBulkDownload,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it })
                            ) {
                                val allTracks by vm.tracks.collectAsState()
                                val bulkDownloadedIds by vm.downloadedIds.collectAsState()
                                val bulkDownloadingIds by vm.downloadingIds.collectAsState()
                                val bulkDownloadProgress by vm.downloadProgress.collectAsState()
                                val bulkDownloadRunning by vm.isBulkDownloading.collectAsState()
                                val bulkDownloadPaused by vm.isBulkDownloadPaused.collectAsState()

                                BulkDownloadScreen(
                                    tracks = allTracks,
                                    downloadedIds = bulkDownloadedIds,
                                    downloadingIds = bulkDownloadingIds,
                                    downloadProgress = bulkDownloadProgress,
                                    isBulkDownloading = bulkDownloadRunning,
                                    isBulkDownloadPaused = bulkDownloadPaused,
                                    coverUrlForTrack = remember(vm) { { vm.coverUrlForTrack(it) } },
                                    onToggleDownload = { track ->
                                        if (vm.isDownloaded(track.id)) vm.removeDownload(track) else vm.downloadTrack(track)
                                    },
                                    onDownloadAll = vm::downloadAllTracks,
                                    onRefreshAllDownloads = vm::refreshAllDownloads,
                                    onTogglePause = vm::toggleBulkDownloadPause,
                                    onCancelAll = vm::cancelBulkDownload,
                                    onClose = vm::closeBulkDownload,
                                )
                            }

                            val showHistory by vm.showHistory.collectAsState()
                            AnimatedVisibility(
                                visible = showHistory,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it })
                            ) {
                                val listenHistory by vm.listenHistory.collectAsState()
                                val historyDownloadedIds by vm.downloadedIds.collectAsState()
                                val historyDownloadingIds by vm.downloadingIds.collectAsState()
                                val historyDownloadProgress by vm.downloadProgress.collectAsState()

                                HistoryScreen(
                                    tracks = listenHistory,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    coverUrlForTrack = remember(vm) { { vm.coverUrlForTrack(it) } },
                                    downloadedIds = historyDownloadedIds,
                                    downloadingIds = historyDownloadingIds,
                                    downloadProgress = historyDownloadProgress,
                                    onBack = vm::closeHistory,
                                    onTrackClick = remember(vm) {
                                        { track ->
                                            notificationPermission.requestIfNeeded()
                                            vm.playHistoryTrack(track)
                                        }
                                    },
                                    onDownload = remember(vm) { { vm.downloadTrack(it) } },
                                    onRemoveDownload = remember(vm) { { vm.removeDownload(it) } },
                                    onAddToPlaylist = remember(vm) { { vm.showAddToPlaylist(it) } },
                                    onPlayAll = remember(vm) {
                                        {
                                            notificationPermission.requestIfNeeded()
                                            vm.playAllHistoryTracks(shuffled = false)
                                        }
                                    },
                                    onPlayRandom = remember(vm) {
                                        {
                                            notificationPermission.requestIfNeeded()
                                            vm.playAllHistoryTracks(shuffled = true)
                                        }
                                    },
                                )
                            }

                            val selectedArtist by vm.selectedArtist.collectAsState()
                            AnimatedVisibility(
                                visible = selectedArtist != null,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it })
                            ) {
                                selectedArtist?.let { artistName ->
                                    val artistTracks by vm.artistTracks.collectAsState()
                                    val artistDownloadedIds by vm.downloadedIds.collectAsState()
                                    val artistDownloadingIds by vm.downloadingIds.collectAsState()
                                    val artistDownloadProgress by vm.downloadProgress.collectAsState()

                                    ArtistScreen(
                                        artistName = artistName,
                                        tracks = artistTracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        coverUrlForTrack = remember(vm) { { vm.coverUrlForTrack(it) } },
                                        downloadedIds = artistDownloadedIds,
                                        downloadingIds = artistDownloadingIds,
                                        downloadProgress = artistDownloadProgress,
                                        onBack = vm::closeArtistPage,
                                        onTrackClick = remember(vm) {
                                            { track ->
                                                notificationPermission.requestIfNeeded()
                                                vm.playArtistTrack(track)
                                            }
                                        },
                                        onDownload = remember(vm) { { vm.downloadTrack(it) } },
                                        onRemoveDownload = remember(vm) { { vm.removeDownload(it) } },
                                        onAddToPlaylist = remember(vm) { { vm.showAddToPlaylist(it) } },
                                        onPlayAll = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.playAllArtistTracks(shuffled = false)
                                            }
                                        },
                                        onPlayRandom = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.playAllArtistTracks(shuffled = true)
                                            }
                                        },
                                        onMiniPlayerClick = remember(vm) { { vm.openFullPlayer() } },
                                        onPlayPause = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.togglePlayPause()
                                            }
                                        },
                                        onNext = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.nextTrack()
                                            }
                                        },
                                        onPrevious = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.previousTrack()
                                            }
                                        },
                                        offlineOnlyMode = offlineOnlyMode,
                                        selectedTab = selectedTab,
                                        onTabSelected = remember(vm) {
                                            { tab ->
                                                vm.closeArtistPage()
                                                vm.setSelectedTab(tab)
                                            }
                                        },
                                        onClosePlaylist = remember(vm) { { vm.closePlaylist() } },
                                    )
                                }
                            }

                            val selectedAlbum by vm.selectedAlbum.collectAsState()
                            AnimatedVisibility(
                                visible = selectedAlbum != null,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it })
                            ) {
                                selectedAlbum?.let { albumName ->
                                    val albumTracks by vm.albumTracks.collectAsState()
                                    val albumDownloadedIds by vm.downloadedIds.collectAsState()
                                    val albumDownloadingIds by vm.downloadingIds.collectAsState()
                                    val albumDownloadProgress by vm.downloadProgress.collectAsState()

                                    AlbumScreen(
                                        albumName = albumName,
                                        tracks = albumTracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        coverUrlForTrack = remember(vm) { { vm.coverUrlForTrack(it) } },
                                        downloadedIds = albumDownloadedIds,
                                        downloadingIds = albumDownloadingIds,
                                        downloadProgress = albumDownloadProgress,
                                        onBack = vm::closeAlbumPage,
                                        onTrackClick = remember(vm) {
                                            { track ->
                                                notificationPermission.requestIfNeeded()
                                                vm.playAlbumTrack(track)
                                            }
                                        },
                                        onDownload = remember(vm) { { vm.downloadTrack(it) } },
                                        onRemoveDownload = remember(vm) { { vm.removeDownload(it) } },
                                        onAddToPlaylist = remember(vm) { { vm.showAddToPlaylist(it) } },
                                        onPlayAll = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.playAllAlbumTracks(shuffled = false)
                                            }
                                        },
                                        onPlayRandom = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.playAllAlbumTracks(shuffled = true)
                                            }
                                        },
                                        onMiniPlayerClick = remember(vm) { { vm.openFullPlayer() } },
                                        onPlayPause = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.togglePlayPause()
                                            }
                                        },
                                        onNext = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.nextTrack()
                                            }
                                        },
                                        onPrevious = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.previousTrack()
                                            }
                                        },
                                        offlineOnlyMode = offlineOnlyMode,
                                        selectedTab = selectedTab,
                                        onTabSelected = remember(vm) {
                                            { tab ->
                                                vm.closeAlbumPage()
                                                vm.setSelectedTab(tab)
                                            }
                                        },
                                        onClosePlaylist = remember(vm) { { vm.closePlaylist() } },
                                    )
                                }
                            }

                            val currentPlaylist by vm.currentPlaylist.collectAsState()
                            AnimatedVisibility(
                                visible = currentPlaylist != null,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it })
                            ) {
                                currentPlaylist?.let { playlist ->
                                    val playlistTracks by vm.currentPlaylistTracks.collectAsState()
                                    val playlistEditMode by vm.playlistEditMode.collectAsState()
                                    val playlistDownloadedIds by vm.downloadedIds.collectAsState()
                                    val playlistDownloadingIds by vm.downloadingIds.collectAsState()
                                    val playlistDownloadProgress by vm.downloadProgress.collectAsState()

                                    PlaylistScreen(
                                        playlist = playlist,
                                        tracks = playlistTracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        coverUrlForTrack = remember(vm) { { vm.coverUrlForTrack(it) } },
                                        downloadedIds = playlistDownloadedIds,
                                        downloadingIds = playlistDownloadingIds,
                                        downloadProgress = playlistDownloadProgress,
                                        canEdit = vm.canEditPlaylist(playlist),
                                        editMode = playlistEditMode,
                                        onToggleEditMode = vm::togglePlaylistEditMode,
                                        onBack = vm::closePlaylist,
                                        onTrackClick = remember(vm) {
                                            { track ->
                                                notificationPermission.requestIfNeeded()
                                                vm.playPlaylistTrack(track)
                                            }
                                        },
                                        onDownload = remember(vm) { { vm.downloadTrack(it) } },
                                        onRemoveDownload = remember(vm) { { vm.removeDownload(it) } },
                                        onAddToPlaylist = remember(vm) { { vm.showAddToPlaylist(it) } },
                                        onRemoveFromPlaylist = remember(vm, playlist) {
                                            { track -> vm.removeFromPlaylist(playlist, track) }
                                        },
                                        onPlayAll = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.playAllPlaylistTracks(shuffled = false)
                                            }
                                        },
                                        onPlayRandom = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.playAllPlaylistTracks(shuffled = true)
                                            }
                                        },
                                        onRename = remember(vm, playlist) {
                                            { newName -> vm.renamePlaylist(playlist, newName) }
                                        },
                                        onSetVisibility = remember(vm, playlist) {
                                            { isPublic -> vm.setPlaylistVisibility(playlist, isPublic) }
                                        },
                                        onReorder = remember(vm, playlist) {
                                            { newOrder -> vm.reorderPlaylist(playlist, newOrder) }
                                        },
                                        onOpenAddSongs = vm::openAddSongsToPlaylist,
                                        onMiniPlayerClick = remember(vm) { { vm.openFullPlayer() } },
                                        onPlayPause = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.togglePlayPause()
                                            }
                                        },
                                        onNext = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.nextTrack()
                                            }
                                        },
                                        onPrevious = remember(vm) {
                                            {
                                                notificationPermission.requestIfNeeded()
                                                vm.previousTrack()
                                            }
                                        },
                                        offlineOnlyMode = offlineOnlyMode,
                                        selectedTab = selectedTab,
                                        onTabSelected = remember(vm) {
                                            { tab ->
                                                vm.closePlaylist()
                                                vm.setSelectedTab(tab)
                                            }
                                        },
                                        onClosePlaylist = remember(vm) { { vm.closePlaylist() } },
                                    )

                                    val showAddSongs by vm.showAddSongsToPlaylist.collectAsState()
                                    if (showAddSongs) {
                                        val allTracks by vm.tracks.collectAsState()
                                        val playlistTrackIds = remember(playlistTracks) { playlistTracks.map { it.id }.toSet() }
                                        AddSongsToPlaylistDialog(
                                            availableTracks = remember(allTracks, playlistTrackIds) {
                                                allTracks.filter { it.id !in playlistTrackIds }
                                            },
                                            coverUrlForTrack = remember(vm) { { vm.coverUrlForTrack(it) } },
                                            onDismiss = vm::hideAddSongsToPlaylist,
                                            onConfirm = { tracksToAdd ->
                                                vm.addTracksToPlaylist(playlist, tracksToAdd)
                                                vm.hideAddSongsToPlaylist()
                                            },
                                        )
                                    }
                                }
                            }

                            // Declared last so it draws on top of the artist/album/playlist overlays above —
                            // opening it from a mini-player inside one of those screens (which stays
                            // visible, since opening the player doesn't close them) must not leave it
                            // hidden behind them.
                            AnimatedVisibility(
                                visible = showFullPlayer && currentTrack != null,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it })
                            ) {
                                currentTrack?.let { track ->
                                    val positionMs by vm.musicPlayer.positionMs.collectAsState()
                                    val durationMs by vm.musicPlayer.durationMs.collectAsState()
                                    val loopMode by vm.musicPlayer.loopModeFlow.collectAsState()
                                    val shuffle by vm.musicPlayer.shuffleFlow.collectAsState()
                                    val playbackSpeed by vm.musicPlayer.playbackSpeedFlow.collectAsState()
                                    val lyrics by vm.lyrics.collectAsState()
                                    val parsedLyrics by vm.parsedLyrics.collectAsState()
                                    val isLoadingLyrics by vm.isLoadingLyrics.collectAsState()
                                    val showLyrics by vm.showLyrics.collectAsState()
                                    val queue by vm.musicPlayer.activeQueueFlow.collectAsState()
                                    val downloadedIds by vm.downloadedIds.collectAsState()
                                    val downloadingIds by vm.downloadingIds.collectAsState()

                                    FullPlayerScreen(
                                        track = track,
                                        isPlaying = isPlaying,
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        loopMode = loopMode,
                                        shuffle = shuffle,
                                        playbackSpeed = playbackSpeed,
                                        coverUrl = vm.coverUrlForTrack(track),
                                        lyrics = lyrics,
                                        parsedLyrics = parsedLyrics,
                                        isLoadingLyrics = isLoadingLyrics,
                                        showLyrics = showLyrics,
                                        queue = queue,
                                        downloadedIds = downloadedIds,
                                        downloadingIds = downloadingIds,
                                        onClose = vm::closeFullPlayer,
                                        onPlayPause = vm::togglePlayPause,
                                        onNext = vm::nextTrack,
                                        onPrevious = vm::previousTrack,
                                        onSeek = vm::seekTo,
                                        onToggleLoop = { vm.toggleLoop() },
                                        onToggleShuffle = { vm.toggleShuffle() },
                                        onSpeedChange = { vm.setPlaybackSpeed(it) },
                                        onToggleLyrics = vm::toggleLyrics,
                                        onAddToPlaylist = { vm.showAddToPlaylist(track) },
                                        onPlayTrackAt = { vm.playTrackAt(it) },
                                        onDownload = { vm.downloadTrack(it) },
                                        onAddToPlaylistForTrack = { vm.showAddToPlaylist(it) },
                                        coverUrlProvider = { vm.coverUrlForTrack(it) },
                                        onArtistClick = remember(vm, onArtistClick) {
                                            { name ->
                                                vm.closeFullPlayer()
                                                onArtistClick(name)
                                            }
                                        },
                                        artistClickEnabled = artistLinksEnabled,
                                        onAlbumClick = remember(vm) {
                                            { name ->
                                                vm.closeFullPlayer()
                                                vm.openAlbumPage(name)
                                            }
                                        },
                                        useDynamicBackground = dynamicThemeEnabled || dynamicThemeFullPlayerOnly,
                                        albumArtColor = dynamicAlbumColor?.let { ComposeColor(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
