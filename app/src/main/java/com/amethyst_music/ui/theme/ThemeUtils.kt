package com.amethyst_music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

object ThemeUtils {
    fun isLight(color: Color): Boolean {
        return color.luminance() > 0.5f
    }

    /** True when [color]'s RGB channels are close enough together to read as grayscale. HSL
     * saturation is unreliable for this near the lightness extremes — its denominator shrinks
     * toward 0 or 1, so a few units of compression noise in an otherwise black/white color can
     * read back as high saturation despite looking neutral. Raw channel spread doesn't have
     * that distortion. */
    private fun isAchromatic(color: Color, threshold: Float = 0.08f): Boolean {
        val spread = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
        return spread < threshold
    }

    fun deriveAccent(base: Color): Color {
        // If the background is pure black, use white as accent for AMOLED theme
        if (base.toArgb() == 0xFF000000.toInt()) {
            return Color.White
        }
        
        // If the background is pure white, use AmethystPrimary as accent
        if (base.toArgb() == 0xFFFFFFFF.toInt()) {
            return AmethystPrimary
        }

        val isLight = isLight(base)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)

        // Judged from the raw RGB (see isAchromatic) and captured before the saturation boost
        // below, which otherwise always lands at 0.5+ regardless of the input and would defeat
        // an HSL-saturation-based check here every time.
        val wasAchromatic = isAchromatic(base)

        if (isLight) {
            // For light backgrounds, we want a darker, more saturated accent
            hsl[1] = (hsl[1] + 0.5f).coerceIn(0.6f, 1.0f)
            hsl[2] = (hsl[2] - 0.4f).coerceIn(0.3f, 0.5f)
        } else {
            // Boost saturation and lightness for the accent color on dark backgrounds
            hsl[1] = (hsl[1] + 0.4f).coerceIn(0.5f, 0.9f)
            hsl[2] = (hsl[2] + 0.5f).coerceIn(0.6f, 0.85f)
        }

        // If the base is very desaturated (grayscale), give it a slight purple/blue hue
        if (wasAchromatic) {
            hsl[0] = 270f // Purple hue
            hsl[1] = 0.6f
            if (isLight) hsl[2] = 0.4f
        }

        return Color(ColorUtils.HSLToColor(hsl))
    }

    /** A brighter variant of [base], used as the top of a background gradient (e.g. full-screen player). */
    fun deriveGradientTop(base: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        hsl[2] = (hsl[2] + 0.15f).coerceIn(0f, 0.55f)
        hsl[1] = (hsl[1] * 1.1f).coerceIn(0f, 1f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun derivePanel(base: Color): Color {
        if (base.toArgb() == 0xFFFFFFFF.toInt()) return AmethystPanelLight
        
        val isLight = isLight(base)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        
        if (isLight) {
            // Slightly darker than background for panels in light mode
            hsl[2] = (hsl[2] - 0.05f).coerceIn(0f, 1f)
        } else {
            // Slightly lighter than background for panels in dark mode
            hsl[2] = (hsl[2] + 0.05f).coerceIn(0f, 1f)
        }
        
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun deriveBorder(base: Color): Color {
        if (base.toArgb() == 0xFFFFFFFF.toInt()) return AmethystBorderLight
        
        val isLight = isLight(base)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        
        if (isLight) {
            // Darker than panel for borders in light mode
            hsl[2] = (hsl[2] - 0.15f).coerceIn(0f, 1f)
        } else {
            // Lighter than panel for borders in dark mode
            hsl[2] = (hsl[2] + 0.15f).coerceIn(0f, 1f)
        }
        
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun deriveTextMuted(base: Color): Color {
        val isLight = isLight(base)
        if (isLight) {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(base.toArgb(), hsl)
            hsl[1] = (hsl[1] * 0.5f).coerceIn(0f, 1f)
            hsl[2] = 0.4f
            return Color(ColorUtils.HSLToColor(hsl))
        }

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        
        // Desaturate and make it semi-bright for muted text
        hsl[1] = (hsl[1] * 0.5f).coerceIn(0f, 1f)
        hsl[2] = 0.7f
        
        return Color(ColorUtils.HSLToColor(hsl))
    }
}
