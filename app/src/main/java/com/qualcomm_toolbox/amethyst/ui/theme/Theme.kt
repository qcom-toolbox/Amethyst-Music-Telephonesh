package com.qualcomm_toolbox.amethyst.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmethystDarkColorScheme = darkColorScheme(
    primary = AmethystPrimary,
    onPrimary = Color.White,
    secondary = AmethystAccent,
    onSecondary = AmethystBackground,
    tertiary = AmethystAccent,
    background = AmethystBackground,
    onBackground = AmethystText,
    surface = AmethystPanel,
    onSurface = AmethystText,
    surfaceVariant = AmethystSearchBg,
    onSurfaceVariant = AmethystTextMuted,
    outline = AmethystBorder,
    error = AmethystDanger,
)

@Composable
fun AmethystMusicTheme(
    backgroundColor: Color = AmethystBackground,
    useHarmony: Boolean = true,
    content: @Composable () -> Unit
) {
    val isLight = ThemeUtils.isLight(backgroundColor)
    
    val accent = if (useHarmony) ThemeUtils.deriveAccent(backgroundColor) else AmethystPrimary
    val panel = if (useHarmony) ThemeUtils.derivePanel(backgroundColor) else (if (isLight) AmethystPanelLight else AmethystPanel)
    val border = if (useHarmony) ThemeUtils.deriveBorder(backgroundColor) else (if (isLight) AmethystBorderLight else AmethystBorder)
    val textMuted = if (useHarmony) ThemeUtils.deriveTextMuted(backgroundColor) else (if (isLight) AmethystTextMutedDark else AmethystTextMuted)
    val textColor = if (isLight) AmethystTextDark else AmethystText

    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent,
            onSecondary = backgroundColor,
            tertiary = accent,
            background = backgroundColor,
            onBackground = textColor,
            surface = panel,
            onSurface = textColor,
            surfaceVariant = panel,
            onSurfaceVariant = textMuted,
            outline = border,
            error = AmethystDanger,
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent,
            onSecondary = backgroundColor,
            tertiary = accent,
            background = backgroundColor,
            onBackground = textColor,
            surface = panel,
            onSurface = textColor,
            surfaceVariant = panel,
            onSurfaceVariant = textMuted,
            outline = border,
            error = AmethystDanger,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
