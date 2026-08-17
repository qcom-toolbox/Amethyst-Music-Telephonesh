package com.amethyst_music.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.amethyst_music.data.ArtistUtils

private const val ARTIST_TAG = "artist"

/** Current app UI language ("en", "fr", …), following per-app locale overrides. */
@Composable
fun currentAppLanguage(): String = LocalConfiguration.current.locales[0].language

/**
 * Renders a track's raw artist string as one tap target per split name, joined by ", " as
 * plain (non-tappable) text — e.g. a "Drake, Travis Scott" tag becomes two independently
 * tappable names. Wherever a track's artist would otherwise render as plain text, use this
 * instead so tapping a name can open that artist's page.
 *
 * When [enabled] is false, renders the same text as plain (non-interactive) content instead
 * of just no-op'ing [onArtistClick] — ClickableText's tap detector consumes touches across
 * its whole bounds regardless of what the callback does, which would otherwise silently
 * swallow taps meant for an ancestor's own click handler (e.g. "tap row to play").
 */
@Composable
fun ArtistNameText(
    artist: String,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    suffix: String? = null,
    enabled: Boolean = true,
) {
    val lang = currentAppLanguage()
    val names = remember(artist, lang) {
        ArtistUtils.splitArtistNames(ArtistUtils.fixEntities(artist, lang))
    }
    if (names.isEmpty()) return

    val annotated = remember(names, suffix) {
        buildAnnotatedString {
            names.forEachIndexed { index, name ->
                pushStringAnnotation(tag = ARTIST_TAG, annotation = name)
                append(name)
                pop()
                if (index != names.lastIndex) append(", ")
            }
            suffix?.let { append(it) }
        }
    }

    val baseStyle = LocalTextStyle.current
    val style = remember(baseStyle, color, fontSize, fontWeight) {
        baseStyle.merge(
            TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
            )
        )
    }

    if (enabled) {
        ClickableText(
            text = annotated,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            onClick = { offset ->
                annotated.getStringAnnotations(tag = ARTIST_TAG, start = offset, end = offset)
                    .firstOrNull()?.let { onArtistClick(it.item) }
            }
        )
    } else {
        Text(
            text = annotated,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}
