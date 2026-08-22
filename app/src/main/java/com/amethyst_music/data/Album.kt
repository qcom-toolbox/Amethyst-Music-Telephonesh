package com.amethyst_music.data

/**
 * There's no separate album entity fetched from the backend — this is derived client-side by
 * grouping tracks that share [Track.album], matching how [ArtistUtils] derives artist grouping.
 * Album names are unique in the backend (`albums.name` has a UNIQUE key), so the name alone is
 * a safe grouping/navigation key.
 */
data class AlbumSummary(
    val name: String,
    val trackCount: Int,
    val coverTrack: Track,
)
