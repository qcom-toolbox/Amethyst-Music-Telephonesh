package com.amethyst_music.data

/**
 * There's no artist entity fetched from the backend — this is derived client-side by grouping
 * tracks whose (possibly multi-name) [Track.artist] field splits to include this name, via
 * [ArtistUtils], matching how [AlbumSummary] derives album grouping.
 */
data class ArtistSummary(
    val name: String,
    val trackCount: Int,
    val coverTrack: Track,
)
