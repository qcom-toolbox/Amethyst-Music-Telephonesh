package com.qualcomm_toolbox.amethyst.player

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.qualcomm_toolbox.amethyst.MainActivity

@UnstableApi
class MusicPlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var lastSyncTime = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        syncMediaSessionThrottled()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        Log.d(TAG, "onGetSession from ${controllerInfo.packageName}")
        syncMediaSessionThrottled()
        return mediaSession
    }

    private fun syncMediaSessionThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < 2000) return // 2 second throttle - critical fix
        lastSyncTime = now

        syncMediaSession()
    }

    private fun syncMediaSession() {
        val player = MusicPlayer.activePlayer ?: return

        if (mediaSession == null || mediaSession?.player !== player) {
            Log.d(TAG, "Creating new MediaLibrarySession")

            mediaSession?.release()

            mediaSession = MediaLibrarySession.Builder(this, player, AmethystLibraryCallback())
                .setSessionActivity(createSessionActivityIntent())
                .build()

            addSession(mediaSession!!)
        }
    }

    private fun createSessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private class AmethystLibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (MusicPlayer.activePlayer?.playWhenReady != true) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MusicPlaybackService"
        const val ACTION_SYNC = "com.qualcomm_toolbox.amethyst.SYNC_SESSION"
    }
}
