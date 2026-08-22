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
        // HSL saturation is unreliable for judging "is this actually a color" here: for dark
        // colors the saturation formula divides by a shrinking denominator as lightness → 0, so
        // even a few units of JPEG compression noise in an otherwise-black pixel (e.g. RGB
        // 18,17,19) can read back as high HSL saturation despite being visually grayscale — and
        // the swatches picked below (dark-muted first) are dark by definition, so this hits
        // constantly. Judge "real color" from the raw RGB channel spread instead, which isn't
        // distorted by lightness.
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val channelSpread = (maxOf(r, g, b) - minOf(r, g, b)) / 255f
        val isAchromatic = channelSpread < 0.08f

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)

        if (isAchromatic) {
            // ColorUtils reports hue as 0 (red) for achromatic colors, since hue is undefined
            // there. Leave truly colorless art neutral instead of inventing a hue/saturation
            // for it — that fabricated hue is why "black and white" covers were still landing
            // on an arbitrary orange or green.
            hsl[1] = 0f
        } else {
            // Enough saturation to read as a real color, but capped so a highly saturated
            // swatch (e.g. a vibrant-swatch fallback) doesn't turn the screen into a neon wall.
            hsl[1] = hsl[1].coerceIn(0.25f, 0.55f)
        }
        hsl[2] = hsl[2].coerceIn(0.05f, 0.22f)
        val clampedArgb = ColorUtils.HSLToColor(hsl)
        return clampedArgb.toLong() and 0xFFFFFFFFL
    }
}
