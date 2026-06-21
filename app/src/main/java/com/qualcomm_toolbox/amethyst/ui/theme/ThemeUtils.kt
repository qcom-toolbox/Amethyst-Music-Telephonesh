package com.qualcomm_toolbox.amethyst.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

object ThemeUtils {
    fun isLight(color: Color): Boolean {
        return color.luminance() > 0.5f
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
        if (hsl[1] < 0.1f) {
            hsl[0] = 270f // Purple hue
            hsl[1] = 0.6f
            if (isLight) hsl[2] = 0.4f
        }
        
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
