package com.amethyst_music.ui.theme

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import okhttp3.OkHttpClient

object AlbumArtColorExtractor {

    /**
     * Downloads [coverUrl] and extracts a dark, muted color suitable as a background for the
     * "Dynamic" theme. Returns null on any failure (no artwork, decode error, etc). This does
     * blocking I/O and CPU work — call from a background dispatcher.
     */
    suspend fun extractBackgroundColor(
        context: Context,
        okHttpClient: OkHttpClient?,
        coverUrl: String,
    ): Long? {
        return try {
            val loader = ImageLoader.Builder(context)
                .okHttpClient(okHttpClient ?: OkHttpClient())
                .build()
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .allowHardware(false)
                .build()
            val result = loader.execute(request) as? SuccessResult ?: return null
            val bitmap = result.drawable.toBitmap()
            val palette = Palette.from(bitmap).generate()
            val swatch = palette.darkMutedSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
                ?: palette.vibrantSwatch
                ?: return null
            clampForBackground(swatch.rgb)
        } catch (e: Exception) {
            null
        }
    }

    /** Darkens/desaturates an arbitrary color so it stays comfortable as a full-screen background. */
    private fun clampForBackground(argb: Int): Long {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[2] = hsl[2].coerceIn(0.05f, 0.22f)
        if (hsl[1] < 0.25f) hsl[1] = 0.25f
        val clampedArgb = ColorUtils.HSLToColor(hsl)
        return clampedArgb.toLong() and 0xFFFFFFFFL
    }
}
