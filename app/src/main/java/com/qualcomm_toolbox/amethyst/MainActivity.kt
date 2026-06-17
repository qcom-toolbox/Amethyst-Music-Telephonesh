package com.qualcomm_toolbox.amethyst

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import coil.compose.LocalImageLoader
import com.qualcomm_toolbox.amethyst.data.ServerPreferences
import com.qualcomm_toolbox.amethyst.ui.components.AddToPlaylistDialog
import com.qualcomm_toolbox.amethyst.ui.screens.FullPlayerScreen
import com.qualcomm_toolbox.amethyst.ui.screens.LoginScreen
import com.qualcomm_toolbox.amethyst.ui.screens.MainScreen
import com.qualcomm_toolbox.amethyst.ui.screens.ServerSetupScreen
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystMusicTheme
import com.qualcomm_toolbox.amethyst.util.NotificationPermissionHelper
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

            androidx.compose.runtime.LaunchedEffect(backgroundColor) {
                val colorInt = backgroundColor.toInt()
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(colorInt),
                    navigationBarStyle = SystemBarStyle.dark(colorInt),
                )
            }
            
            AmethystMusicTheme(
                backgroundColor = ComposeColor(backgroundColor),
                useHarmony = useHarmony
            ) {
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

                val trustAllCerts by vm.trustAllCerts.collectAsState()
                val context = LocalContext.current
                val imageLoader = remember(vm.okHttpClient(), trustAllCerts) {
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
                        color = ComposeColor(backgroundColor),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (screen) {
                                AppScreen.Setup -> ServerSetupScreen(
                                    isLoading = isLoading,
                                    error = error,
                                    initialTrustAllCerts = trustAllCerts,
                                    onConnect = { url, trustAll ->
                                        vm.clearError()
                                        vm.saveServer(url, trustAll)
                                    },
                                )
                                AppScreen.Login -> LoginScreen(
                                    siteName = siteName,
                                    savedUsername = prefs.savedUsername,
                                    isLoading = isLoading,
                                    error = error,
                                    hasOfflineLibrary = vm.hasOfflineLibrary,
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
                                        coverUrlForTrack = vm::coverUrlForTrack,
                                        onTabSelected = vm::setSelectedTab,
                                        onSearchChange = vm::setSearchQuery,
                                        onTrackClick = { track ->
                                            notificationPermission.requestIfNeeded()
                                            vm.playTrack(track)
                                        },
                                        onPlaylistClick = { playlist ->
                                            notificationPermission.requestIfNeeded()
                                            vm.playPlaylist(playlist)
                                        },
                                        onDownload = vm::downloadTrack,
                                        onRemoveDownload = vm::removeDownload,
                                        onRefresh = vm::loadLibrary,
                                        onLogout = vm::logout,
                                        onExitOffline = vm::exitOfflineMode,
                                        onMiniPlayerClick = vm::openFullPlayer,
                                        onTogglePlay = {
                                            notificationPermission.requestIfNeeded()
                                            vm.togglePlayPause()
                                        },
                                        onUploadTrack = { t, a, g, m, mn, c, cn ->
                                            vm.uploadTrack(t, a, g, m, mn, c, cn)
                                        },
                                        homeRecommended = homeRecommended,
                                        homePopular = homePopular,
                                        homeHiddenGems = homeHiddenGems,
                                        backgroundColor = backgroundColor,
                                        useHarmony = useHarmony,
                                        onThemeChange = { color, harmony ->
                                            vm.setBackgroundColor(color)
                                            vm.setUseHarmony(harmony)
                                        },
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
                                    val lyrics by vm.lyrics.collectAsState()
                                    val parsedLyrics by vm.parsedLyrics.collectAsState()
                                    val isLoadingLyrics by vm.isLoadingLyrics.collectAsState()
                                    val showLyrics by vm.showLyrics.collectAsState()

                                    FullPlayerScreen(
                                        track = track,
                                        isPlaying = isPlaying,
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        loopMode = loopMode,
                                        shuffle = shuffle,
                                        coverUrl = vm.coverUrlForTrack(track),
                                        lyrics = lyrics,
                                        parsedLyrics = parsedLyrics,
                                        isLoadingLyrics = isLoadingLyrics,
                                        showLyrics = showLyrics,
                                        onClose = vm::closeFullPlayer,
                                        onPlayPause = vm::togglePlayPause,
                                        onNext = vm::nextTrack,
                                        onPrevious = vm::previousTrack,
                                        onSeek = vm::seekTo,
                                        onToggleLoop = { vm.toggleLoop() },
                                        onToggleShuffle = { vm.toggleShuffle() },
                                        onToggleLyrics = vm::toggleLyrics,
                                        onAddToPlaylist = { vm.showAddToPlaylist(track) },
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
