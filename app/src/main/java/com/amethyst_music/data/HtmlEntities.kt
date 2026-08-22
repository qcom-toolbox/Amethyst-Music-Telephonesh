package com.amethyst_music.data

/**
 * Decodes the small set of HTML entities the backend can leave in any text field it sanitizes
 * with PHP's `htmlspecialchars(..., ENT_QUOTES, 'UTF-8')` — track titles, artist names, genres,
 * album names, playlist names, etc. Without this, e.g. a title containing an apostrophe shows
 * up verbatim as "Don&#039;t Stop" in the UI instead of "Don't Stop".
 */
object HtmlEntities {
    fun decode(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
}
