package com.amethyst_music.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ArtistBio(val extract: String, val pageUrl: String)

/**
 * Direct client -> Wikipedia lookups for the artist bio section. No backend involved: the
 * Wikipedia REST API sends Access-Control-Allow-Origin: *, so it's fetchable straight from
 * any HTTP client. Every call is a fresh live lookup; nothing is cached or persisted.
 */
object WikipediaClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Resolves a bio for [artistName] in [lang], falling back to English if [lang] isn't
     * English and nothing was found there. Runs on the calling coroutine directly (blocking
     * network calls inside) — callers should invoke this from a Dispatchers.IO context.
     */
    fun fetchBio(artistName: String, lang: String): ArtistBio? {
        fetchInLanguage(artistName, lang)?.let { return it }
        if (!lang.equals("en", ignoreCase = true)) {
            return fetchInLanguage(artistName, "en")
        }
        return null
    }

    private fun fetchInLanguage(artistName: String, lang: String): ArtistBio? {
        // Step 1: direct summary lookup.
        summary(artistName, lang)?.let { return it }

        // Step 2: search fallback, skipping the disambiguation page itself.
        val candidates = search(artistName, lang)
        for (title in candidates) {
            if (title.equals(artistName, ignoreCase = true)) continue
            summary(title, lang)?.let { return it }
        }
        return null
    }

    /** Returns null on HTTP failure, disambiguation pages, or an empty extract. */
    private fun summary(title: String, lang: String): ArtistBio? {
        return try {
            val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/${encodeTitle(title)}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AmethystMusic/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                if (json.optString("type") == "disambiguation") return null
                val extract = json.optString("extract")
                if (extract.isBlank()) return null
                val pageUrl = json.optJSONObject("content_urls")
                    ?.optJSONObject("desktop")
                    ?.optString("page")
                    ?.takeIf { it.isNotBlank() }
                    ?: "https://$lang.wikipedia.org/wiki/${encodeTitle(title)}"
                ArtistBio(extract, pageUrl)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun search(query: String, lang: String): List<String> {
        return try {
            val url = "https://$lang.wikipedia.org/w/rest.php/v1/search/page".toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "5")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AmethystMusic/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val pages = JSONObject(body).optJSONArray("pages") ?: return emptyList()
                (0 until pages.length()).map { pages.getJSONObject(it).optString("title") }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeTitle(title: String): String =
        URLEncoder.encode(title, "UTF-8").replace("+", "%20")
}
