package com.amethyst_music.data

/**
 * There's no dedicated artist entity in the backend — [Track.artist] is a free-text string
 * that can hold one or several names ("Drake, Travis Scott", "Coldplay & Rihanna"). These
 * helpers derive per-artist grouping client-side from that string, matching the splitting
 * rules used by the web app so artist pages agree across platforms.
 */
object ArtistUtils {
    // The word for "and" in each of the app's supported UI languages. Used both to decode
    // "&amp;" into readable text and (below) as extra split separators, so a track decoded
    // under any of these languages still splits correctly.
    private val AND_WORDS: Map<String, String> = mapOf(
        "en" to "and",
        "fr" to "et",
        "de" to "und",
        "it" to "e",
        "es" to "y",
        "rm" to "e",
        "ru" to "и",
        "zh" to "和",
        "ja" to "と",
        "hi" to "और",
        "mn" to "ба",
    )

    // Order matters: longer/more-specific separators must be tried before shorter ones that
    // could be a prefix of them (e.g. "&amp;" before "&"). Word separators require whitespace
    // on both sides so names like "Sixx", "Andy", "AC/DC", "Foxx" are never split.
    private val SPLIT_REGEX = Regex(
        "\\s*,\\s*|\\s*&amp;\\s*|\\s*&\\s*|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+featuring\\s+|\\s+vs\\.?\\s+|\\s+x\\s+|" +
            "\\s+(?:${AND_WORDS.values.distinct().joinToString("|") { Regex.escape(it) }})\\s+",
        RegexOption.IGNORE_CASE
    )

    /**
     * Decodes the HTML entities the backend applies when storing track metadata: the literal
     * apostrophe entity is restored, and "&amp;" is converted to the word for "and" in the
     * current UI language — a deliberate app convention (mirroring the web app's fixEntities),
     * not just entity-decoding.
     */
    fun fixEntities(raw: String, lang: String): String {
        return raw.replace("&#039;", "'").replace("&amp;", andWord(lang))
    }

    /** The word for "and" in [lang] (falling back to English), used both by [fixEntities] and
     * to join split artist names back together for display. */
    fun andWord(lang: String): String {
        val code = lang.take(2).lowercase()
        return AND_WORDS[code] ?: "and"
    }

    fun splitArtistNames(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Case-insensitive match of a single artist name against a track's raw artist field. */
    fun trackBelongsToArtist(trackArtist: String, artistName: String, lang: String): Boolean {
        val target = artistName.trim().lowercase()
        return splitArtistNames(fixEntities(trackArtist, lang)).any { it.lowercase() == target }
    }
}
