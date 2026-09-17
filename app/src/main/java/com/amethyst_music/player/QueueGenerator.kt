package com.amethyst_music.player

import com.amethyst_music.data.Track
import com.amethyst_music.data.UserAffinity
import kotlin.math.ln
import kotlin.random.Random

/**
 * Client-side "DJ continuation" used to fill a queue after its seed track, mirroring the web
 * client's replacement for the old play-count-sorted queue. Given a starting song, candidates
 * are split into same-artist / same-genre / everything-else, each group is picked by weighted
 * random selection (never a hard sort-and-cut, so two calls with the same seed never produce an
 * identical batch), then threaded together so the same artist never repeats back-to-back.
 */
object QueueGenerator {
    private const val ARTIST_SHARE = 0.30
    private const val GENRE_SHARE = 0.30
    private const val DISCOVERY_SHARE = 0.28
    // Remaining ~0.12 is genuinely unweighted random picks.

    private const val ARTIST_BONUS = 40.0
    private const val GENRE_BONUS = 15.0
    private const val ALBUM_BONUS = 8.0
    private const val AFFINITY_GENRE_WEIGHT = 5.0
    private const val AFFINITY_ARTIST_WEIGHT = 8.0
    private const val AFFINITY_ALBUM_WEIGHT = 6.0
    private const val POPULARITY_WEIGHT = 1.5
    private const val JITTER_WEIGHT = 6.0

    private fun sameArtist(a: Track, b: Track): Boolean =
        a.artist.trim().equals(b.artist.trim(), ignoreCase = true)

    private fun score(t: Track, seed: Track, affinity: UserAffinity): Double {
        var s = 0.0
        if (sameArtist(t, seed)) s += ARTIST_BONUS
        if (t.genre == seed.genre) s += GENRE_BONUS
        if (seed.albumId != null && t.albumId == seed.albumId) s += ALBUM_BONUS
        s += (affinity.genre[t.genre] ?: 0.0) * AFFINITY_GENRE_WEIGHT
        s += (affinity.artist[t.artist] ?: 0.0) * AFFINITY_ARTIST_WEIGHT
        s += (t.albumId?.let { affinity.album[it] } ?: 0.0) * AFFINITY_ALBUM_WEIGHT
        s += ln(1.0 + t.playCount) * POPULARITY_WEIGHT
        s += Random.nextDouble() * JITTER_WEIGHT
        return s.coerceAtLeast(0.01)
    }

    /** Weighted random sample without replacement (Efraimidis-Spirakis): each candidate gets a
     * key of -ln(U)/weight, and the n smallest keys win — a high score is likelier but never
     * guaranteed, unlike sorting by score and slicing the top n. */
    private fun weightedSample(candidates: List<Track>, n: Int, weightOf: (Track) -> Double): List<Track> {
        if (n <= 0 || candidates.isEmpty()) return emptyList()
        return candidates
            .map { it to (-ln(Random.nextDouble().coerceAtLeast(1e-12)) / weightOf(it)) }
            .sortedBy { it.second }
            .take(n)
            .map { it.first }
    }

    private fun uniformSample(candidates: List<Track>, n: Int): List<Track> =
        if (n <= 0 || candidates.isEmpty()) emptyList() else candidates.shuffled().take(n)

    /** Avoids placing the same artist twice in a row, and tries to avoid repeating an artist
     * used in the last 2-3 picks whenever there's an alternative, falling back gracefully
     * (same-as-previous, then anything at all) when there genuinely isn't one. [recentArtists]
     * seeds the "last few picks" window from whatever precedes this batch in the real queue, so
     * a batch boundary can't create a repeat either. */
    private fun threadByArtist(picked: List<Track>, recentArtists: List<String>): List<Track> {
        val pool = picked.shuffled().toMutableList()
        val result = mutableListOf<Track>()
        val history = recentArtists.toMutableList()
        while (pool.isNotEmpty()) {
            val avoidLast3 = history.takeLast(3).toSet()
            var idx = pool.indexOfFirst { it.artist !in avoidLast3 }
            if (idx == -1) idx = pool.indexOfFirst { it.artist != history.lastOrNull() }
            if (idx == -1) idx = 0
            val next = pool.removeAt(idx)
            result += next
            history += next.artist
        }
        return result
    }

    fun generateBatch(
        seed: Track,
        pool: List<Track>,
        exclude: Set<Int>,
        affinity: UserAffinity,
        recentArtists: List<String> = emptyList(),
        batchSize: Int = 40,
    ): List<Track> {
        val candidates = pool.filter { it.id != seed.id && it.id !in exclude }
        if (candidates.isEmpty()) return emptyList()

        val sameArtistPool = candidates.filter { sameArtist(it, seed) }
        val sameArtistIds = sameArtistPool.map { it.id }.toSet()
        val sameGenrePool = candidates.filter { it.id !in sameArtistIds && it.genre == seed.genre }
        val sameGenreIds = sameGenrePool.map { it.id }.toSet()
        val restPool = candidates.filter { it.id !in sameArtistIds && it.id !in sameGenreIds }

        val artistCount = (batchSize * ARTIST_SHARE).toInt()
        val genreCount = (batchSize * GENRE_SHARE).toInt()
        val discoveryCount = (batchSize * DISCOVERY_SHARE).toInt()
        val randomCount = batchSize - artistCount - genreCount - discoveryCount

        val picked = mutableListOf<Track>()
        val pickedIds = mutableSetOf<Int>()

        fun take(list: List<Track>) {
            picked += list
            pickedIds += list.map { it.id }
        }

        take(weightedSample(sameArtistPool, artistCount) { score(it, seed, affinity) })
        take(weightedSample(sameGenrePool, genreCount) { score(it, seed, affinity) })

        val restRemaining = restPool.filter { it.id !in pickedIds }
        take(weightedSample(restRemaining, discoveryCount) { score(it, seed, affinity) })

        val restForRandom = restPool.filter { it.id !in pickedIds }
        take(uniformSample(restForRandom, randomCount))

        // Backfill from whatever's left over if any group came up short (small library).
        val shortfall = batchSize - picked.size
        if (shortfall > 0) {
            val leftover = candidates.filter { it.id !in pickedIds }
            take(uniformSample(leftover, shortfall))
        }

        return threadByArtist(picked, recentArtists)
    }
}
