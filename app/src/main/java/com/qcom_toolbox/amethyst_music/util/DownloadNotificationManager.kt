package com.qcom_toolbox.amethyst_music.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qcom_toolbox.amethyst_music.R

/** Shows a single ongoing notification while downloads are in progress, and clears it once none remain. */
class DownloadNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 2001
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun update(completed: Int, total: Int, progressPercent: Int) {
        if (!hasPermission()) return
        val text = if (total > 1) {
            context.getString(
                R.string.download_notification_progress_multi,
                (completed + 1).coerceAtMost(total),
                total,
            )
        } else {
            context.getString(R.string.download_notification_progress_single, progressPercent)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_notification)
            .setContentTitle(context.getString(R.string.download_notification_title))
            .setContentText(text)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun clear() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
