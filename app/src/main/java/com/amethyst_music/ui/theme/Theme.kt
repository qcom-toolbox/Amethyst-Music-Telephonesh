package com.amethyst_music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
    // Readable against the accent rather than always white — see ThemeUtils.readableOn.
    val onAccent = ThemeUtils.readableOn(accent)

    // Every slot is filled from the theme's own colors. Slots left unset fall back to
    // Material's stock lavender/pink palette, which showed up as off-hue text on selected
    // chips, snackbars, dialogs etc. whenever the base color wasn't purple (e.g. Dynamic).
    val container = lerp(panel, accent, 0.3f)
    val onContainer = if (ThemeUtils.isLight(container)) AmethystTextDark else AmethystText
    val inverseSurface = if (isLight) AmethystTextDark else AmethystText
    val inverseOnSurface = if (isLight) AmethystText else AmethystTextDark
    // Snackbar actions sit on inverseSurface, so they need an accent tuned for that background.
    val inversePrimary = ThemeUtils.withLightness(accent, if (isLight) 0.75f else 0.4f)
    val surfaceLow = lerp(backgroundColor, panel, 0.5f)
    val surfaceHigh = lerp(panel, border, 0.25f)
    val surfaceHighest = lerp(panel, border, 0.5f)

    val baseScheme = if (isLight) lightColorScheme() else darkColorScheme()
    val colorScheme = baseScheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = inversePrimary,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = container,
        onTertiaryContainer = onContainer,
        background = backgroundColor,
        onBackground = textColor,
        surface = panel,
        onSurface = textColor,
        surfaceVariant = panel,
        onSurfaceVariant = textMuted,
        surfaceTint = accent,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        outline = border,
        outlineVariant = lerp(panel, border, 0.6f),
        scrim = Color.Black,
        surfaceBright = surfaceHighest,
        surfaceDim = backgroundColor,
        surfaceContainerLowest = backgroundColor,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = panel,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
        error = AmethystDanger,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
