package com.amethyst_music.data

/**
 * Matching shared by every search box. Punctuation is one-directional:
 *
 *  - Punctuation in the *text* is optional — "im crunk" finds "I'm Crunk", "hello world" finds
 *    "Hello, World". Text punctuation the query didn't ask for is simply skipped over.
 *  - Punctuation in the *query* is required — "i'm crunk" does not find "Im Crunk", and
 *    "hello, world" does not find "Hello World". If you typed it, the result must contain it.
 *
 * Case is ignored, and the apostrophe variants (' ’ ‘ ʼ ` ´) count as the same character, since
 * tags copied from streaming sites or typed on a phone keyboard often use ’ where others use '.
 * Whitespace is never skipped, so word boundaries in the query still have to line up.
 */
object SearchText {
    private const val APOSTROPHES = "'’‘ʼ`´"

    /** Trims and lowercases raw search-box input. An empty result means "not searching". */
    fun prepareQuery(raw: String): String = raw.trim().lowercase()

    /** Whether [text] contains [query] (from [prepareQuery]) under the rules above. */
    fun matches(text: String, query: String): Boolean {
        if (query.isEmpty()) return true
        for (start in text.indices) {
            if (matchesAt(text, start, query)) return true
        }
        return false
    }

    private fun matchesAt(text: String, start: Int, query: String): Boolean {
        var q = 0
        var t = start
        while (q < query.length) {
            if (t >= text.length) return false
            when {
                canonical(query[q]) == canonical(text[t]) -> { q++; t++ }
                isSkippable(text[t]) -> t++
                else -> return false
            }
        }
        return true
    }

    private fun canonical(c: Char): Char = if (c in APOSTROPHES) '\'' else c.lowercaseChar()

    /** Punctuation and symbols in the text — anything that isn't a letter, digit or space. */
    private fun isSkippable(c: Char): Boolean = !c.isLetterOrDigit() && !c.isWhitespace()
}
