package com.qualcomm_toolbox.amethyst.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmethystColorScheme = darkColorScheme(
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
    val accent = if (useHarmony) ThemeUtils.deriveAccent(backgroundColor) else AmethystAccent
    val panel = if (useHarmony) ThemeUtils.derivePanel(backgroundColor) else AmethystPanel
    val border = if (useHarmony) ThemeUtils.deriveBorder(backgroundColor) else AmethystBorder
    val textMuted = if (useHarmony) ThemeUtils.deriveTextMuted(backgroundColor) else AmethystTextMuted

    val dynamicColorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = accent,
        onSecondary = backgroundColor,
        tertiary = accent,
        background = backgroundColor,
        onBackground = AmethystText,
        surface = panel,
        onSurface = AmethystText,
        surfaceVariant = panel,
        onSurfaceVariant = textMuted,
        outline = border,
        error = AmethystDanger,
    )

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = Typography,
        content = content,
    )
}
