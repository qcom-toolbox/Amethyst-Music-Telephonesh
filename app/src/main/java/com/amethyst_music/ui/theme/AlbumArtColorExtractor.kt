package com.amethyst_music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import okhttp3.OkHttpClient
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Direct port of the desktop web client's adaptive-theme extraction (index.php's
 * extractPalette/paletteToTheme/tameAdaptiveColor) so both platforms pick the same background
 * from the same cover. Android's own Palette library (androidx.palette) uses median-cut
 * quantization, which splits a genuinely dominant color's shades across several boxes while a
 * small, tightly-clustered accent (a stripe, a color-cast shadow) lands in just one — that one
 * box can then out-populate any single shade of the true majority color, which is how a mostly
 * black-and-white cover with a thin red stripe ended up with a red background, and a yellow
 * cover with a blue-tinted shadow ended up blue. This instead:
 *  1. Buckets pixels on a fixed-size RGB grid (so a color's pixels land in the same handful of
 *     buckets regardless of where they sit in the image, rather than however median-cut happens
 *     to split the color space for this particular image),
 *  2. Discards buckets under 1.5% of the image before they're even eligible to be picked,
 *  3. Scores what's left against named target profiles with population weighted the heaviest.
 */
object AlbumArtColorExtractor {

    private class Target(
        val minSaturation: Float,
        val targetSaturation: Float,
        val minLightness: Float,
        val targetLightness: Float,
        val maxLightness: Float,
    )

    // Same six named profiles as index.php's PALETTE_TARGETS (itself modeled on Android's own
    // Palette API targets).
    private val TARGET_VIBRANT = Target(0.35f, 1.0f, 0.30f, 0.50f, 0.70f)
    private val TARGET_LIGHT_VIBRANT = Target(0.35f, 1.0f, 0.55f, 0.74f, 1.00f)
    private val TARGET_DARK_VIBRANT = Target(0.35f, 1.0f, 0.00f, 0.26f, 0.45f)
    private val TARGET_MUTED = Target(0.00f, 0.3f, 0.30f, 0.50f, 0.70f)
    private val TARGET_LIGHT_MUTED = Target(0.00f, 0.3f, 0.55f, 0.74f, 1.00f)
    private val TARGET_DARK_MUTED = Target(0.00f, 0.3f, 0.00f, 0.26f, 0.45f)

    private const val SAMPLE_SIZE = 64
    private const val QUANTIZATION_STEP = 24
    private const val MIN_POPULATION_FRACTION = 0.015f
    private const val WEIGHT_SATURATION = 2f
    private const val WEIGHT_LIGHTNESS = 3f
    private const val WEIGHT_POPULATION = 5f

    private class Bucket(var count: Int = 0, var rSum: Long = 0, var gSum: Long = 0, var bSum: Long = 0)

    private class Cluster(val rgb: Int, val count: Int, val saturation: Float, val lightness: Float)

    private class ExtractedPalette(
        val vibrant: Int?,
        val lightVibrant: Int?,
        val darkVibrant: Int?,
        val muted: Int?,
        val lightMuted: Int?,
        val darkMuted: Int?,
        val dominant: Int,
    )

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
            val sample = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
            val palette = extractPalette(sample) ?: return null
            // Priority mirrors index.php's paletteToTheme: a dark, muted tone as the base
            // background, falling back toward more vivid/whatever-was-found options only if nothing
            // dark and muted was present.
            val rawBase = palette.darkMuted ?: palette.darkVibrant ?: palette.muted ?: palette.vibrant ?: palette.dominant
            tameAdaptiveColor(rawBase, maxSaturation = 0.5f, maxLightness = 0.20f)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractPalette(bitmap: Bitmap): ExtractedPalette? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val buckets = HashMap<Long, Bucket>()
        val hsl = FloatArray(3)
        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a < 200) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            ColorUtils.RGBToHSL(r, g, b, hsl)
            // Ignore near-black/near-white pixels (letterbox borders, blown highlights) so they
            // can't dominate a bucket just from sheer area.
            if (hsl[2] < 0.03f || hsl[2] > 0.97f) continue
            val key = bucketKey(r, g, b)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.rSum += r
            bucket.gSum += g
            bucket.bSum += b
        }
        if (buckets.isEmpty()) return null

        val allClusters = buckets.values.map { bucket ->
            val rgb = Color.rgb(
                (bucket.rSum / bucket.count).toInt(),
                (bucket.gSum / bucket.count).toInt(),
                (bucket.bSum / bucket.count).toInt(),
            )
            ColorUtils.colorToHSL(rgb, hsl)
            Cluster(rgb, bucket.count, hsl[1], hsl[2])
        }
        val totalPixels = allClusters.sumOf { it.count }
        val dominant = allClusters.maxBy { it.count }.rgb

        // Only clusters actually representative of a meaningful part of the image are eligible
        // to become a named target color — otherwise a tiny, isolated bright spot (a reflection,
        // a thin accent stripe) that happens to score well against a target could be picked even
        // though it's nearly invisible on the actual cover.
        val eligible = allClusters
            .filter { it.count.toFloat() / totalPixels >= MIN_POPULATION_FRACTION }
            .sortedByDescending { it.count }
        if (eligible.isEmpty()) return ExtractedPalette(null, null, null, null, null, null, dominant)

        val maxPopulation = eligible.first().count
        val used = HashSet<Int>()

        fun score(target: Target, c: Cluster): Float {
            if (c.lightness < target.minLightness || c.lightness > target.maxLightness || c.saturation < target.minSaturation) {
                return Float.NEGATIVE_INFINITY
            }
            val saturationScore = 1f - abs(c.saturation - target.targetSaturation)
            val lightnessScore = 1f - abs(c.lightness - target.targetLightness)
            val populationScore = c.count.toFloat() / maxPopulation
            return saturationScore * WEIGHT_SATURATION + lightnessScore * WEIGHT_LIGHTNESS + populationScore * WEIGHT_POPULATION
        }

        fun pick(target: Target): Int? {
            var best: Cluster? = null
            var bestScore = Float.NEGATIVE_INFINITY
            for (c in eligible) {
                if (c.rgb in used) continue
                val s = score(target, c)
                if (s > bestScore) {
                    bestScore = s
                    best = c
                }
            }
            return if (best != null && bestScore > Float.NEGATIVE_INFINITY) {
                used.add(best.rgb)
                best.rgb
            } else {
                null
            }
        }

        return ExtractedPalette(
            vibrant = pick(TARGET_VIBRANT),
            lightVibrant = pick(TARGET_LIGHT_VIBRANT),
            darkVibrant = pick(TARGET_DARK_VIBRANT),
            muted = pick(TARGET_MUTED),
            lightMuted = pick(TARGET_LIGHT_MUTED),
            darkMuted = pick(TARGET_DARK_MUTED),
            dominant = dominant,
        )
    }

    private fun bucketKey(r: Int, g: Int, b: Int): Long {
        val rq = (r.toFloat() / QUANTIZATION_STEP).roundToInt().toLong()
        val gq = (g.toFloat() / QUANTIZATION_STEP).roundToInt().toLong()
        val bq = (b.toFloat() / QUANTIZATION_STEP).roundToInt().toLong()
        return (rq shl 20) or (gq shl 10) or bq
    }

    /** Caps saturation/lightness so a very saturated or bright spot doesn't become the
     * background of the whole app — mirrors index.php's tameAdaptiveColor. Only ever lowers
     * these values, never raises them, so genuinely gray/dark art stays gray/dark instead of
     * having a hue/saturation invented for it. */
    private fun tameAdaptiveColor(argb: Int, maxSaturation: Float, maxLightness: Float): Long {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[1] = hsl[1].coerceAtMost(maxSaturation)
        hsl[2] = hsl[2].coerceAtMost(maxLightness)
        val clamped = ColorUtils.HSLToColor(hsl)
        return clamped.toLong() and 0xFFFFFFFFL
    }
}
