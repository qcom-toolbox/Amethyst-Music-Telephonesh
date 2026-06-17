package com.qualcomm_toolbox.amethyst.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

object ThemeUtils {
    fun deriveAccent(base: Color): Color {
        // If the background is pure black, use white as accent for AMOLED theme
        if (base.toArgb() == 0xFF000000.toInt()) {
            return Color.White
        }

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        
        // Boost saturation and lightness for the accent color
        hsl[1] = (hsl[1] + 0.4f).coerceIn(0.5f, 0.9f)
        hsl[2] = (hsl[2] + 0.5f).coerceIn(0.6f, 0.85f)
        
        // If the base is very desaturated (grayscale), give it a slight purple/blue hue
        if (hsl[1] < 0.1f) {
            hsl[0] = 270f // Purple hue
            hsl[1] = 0.5f
        }
        
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun derivePanel(base: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        
        // Slightly lighter than background for panels
        hsl[2] = (hsl[2] + 0.05f).coerceIn(0f, 1f)
        
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun deriveBorder(base: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        
        // Lighter than panel for borders
        hsl[2] = (hsl[2] + 0.15f).coerceIn(0f, 1f)
        
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun deriveTextMuted(base: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        
        // Desaturate and make it semi-bright for muted text
        hsl[1] = (hsl[1] * 0.5f).coerceIn(0f, 1f)
        hsl[2] = 0.7f
        
        return Color(ColorUtils.HSLToColor(hsl))
    }
}
