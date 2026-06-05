package com.qualcomm_toolbox.amethyst.data

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class PurpleClient(
    private var baseUrl: String,
    trustAllCerts: Boolean = false,
    private val cookieJar: PersistentCookieJar? = null,
) {
    private var trustAllCerts: Boolean = trustAllCerts
    private var client: OkHttpClient = buildHttpClient()

    val okHttpClient: OkHttpClient get() = client

    fun setTrustAllCerts(enabled: Boolean) {
        if (trustAllCerts == enabled) return
        trustAllCerts = enabled
        client = buildHttpClient()
    }

    private fun buildHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
        if (cookieJar != null) {
            builder.cookieJar(cookieJar)
        }
        if (trustAllCerts) {
            UnsafeSsl.applyTo(builder)
        }
        return builder.build()
    }

    fun updateBaseUrl(url: String) {
        baseUrl = ServerPreferences.normalizeServerUrl(url)
        cookieJar?.updateServerUrl(baseUrl)
        client = buildHttpClient()
    }

    fun clearSession() {
        cookieJar?.clear()
    }

    fun hasValidSession(): Boolean = try {
        fetchPage().contains("ALL_MUSIC_DATA")
    } catch (_: Exception) {
        false
    }

    fun indexUrl(): String = "$baseUrl/"

    fun musicUrl(filename: String): String = "$baseUrl/music/$filename"

    fun coverUrl(cover: String): String = "$baseUrl/covers/$cover"

    private fun fetchPage(path: String = "/"): String {
        val url = if (path == "/") indexUrl() else "$baseUrl$path"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PurpleException("Serveur injoignable (${response.code})")
            }
            return response.body?.string()
                ?: throw PurpleException("Réponse vide du serveur")
        }
    }

    fun validateServer(): String {
        val html = fetchPage()
        if (html.contains("MODE INSTALLATION") || html.contains("name=\"install\"")) {
            throw PurpleException("Ce serveur n'est pas encore installé.")
        }
        extractSiteName(html)?.let { return it }
        if (html.contains("name=\"login\"") || html.contains("ALL_MUSIC_DATA")) {
            return "Purple Music"
        }
        throw PurpleException("URL invalide — serveur Purple/Amethyst introuvable.")
    }

    fun login(username: String, password: String) {
        val loginPage = fetchPage()
        val csrf = extractCsrfToken(loginPage)
            ?: throw PurpleException("Impossible de lire le jeton CSRF.")
        if (!loginPage.contains("name=\"login\"") && loginPage.contains("ALL_MUSIC_DATA")) {
            return
        }
        val body = FormBody.Builder()
            .add("csrf_token", csrf)
            .add("username", username)
            .add("password", password)
            .add("login", "")
            .build()
        val request = Request.Builder()
            .url(indexUrl())
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val html = response.body?.string() ?: ""
            extractAuthError(html)?.let { throw PurpleException(it) }
            if (!html.contains("ALL_MUSIC_DATA")) {
                if (html.contains("name=\"login\"")) {
                    throw PurpleException("Identifiants incorrects.")
                }
                if (!response.isSuccessful) {
                    throw PurpleException("Échec de connexion (${response.code}).")
                }
            }
        }
    }

    fun register(username: String, password: String) {
        val loginPage = fetchPage()
        val csrf = extractCsrfToken(loginPage)
            ?: throw PurpleException("Impossible de lire le jeton CSRF.")
        val body = FormBody.Builder()
            .add("csrf_token", csrf)
            .add("username", username)
            .add("password", password)
            .add("register", "")
            .build()
        val request = Request.Builder()
            .url(indexUrl())
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val html = response.body?.string() ?: ""
            extractAuthError(html)?.let { throw PurpleException(it) }
            if (html.contains("ALL_MUSIC_DATA")) return@use
        }
    }

    private fun extractAuthError(html: String): String? {
        val patterns = listOf(
            Regex("""color:var\(--danger\);?">([^<]+)</p>"""),
            Regex("""class="error"[^>]*>([^<]+)</"""),
        )
        for (pattern in patterns) {
            pattern.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return it
            }
        }
        if (html.contains("Nom d'utilisateur déjà pris") || html.contains("déjà pris")) {
            return "Nom d'utilisateur déjà pris."
        }
        if (html.contains("Veuillez patienter")) {
            return "Veuillez patienter avant de réessayer."
        }
        return null
    }

    fun fetchTracks(): List<Track> {
        val html = fetchPage()
        if (html.contains("name=\"login\"") && !html.contains("ALL_MUSIC_DATA")) {
            throw PurpleException("Session expirée — reconnectez-vous.")
        }
        val json = extractJsonArray(html, "ALL_MUSIC_DATA")
            ?: throw PurpleException("Bibliothèque introuvable sur ce serveur.")
        return parseTracks(json)
    }

    fun fetchPlaylists(): List<Playlist> {
        val html = fetchPage()
        return parsePlaylistsFromHtml(html)
    }

    fun fetchPlaylistTracks(ids: List<Int>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val url = "${indexUrl()}?get_playlist_tracks=${ids.joinToString(",")}"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "[]"
            return parseTracks(JSONArray(body))
        }
    }

    fun incrementPlay(trackId: Int) {
        val url = "${indexUrl()}?increment_play=$trackId"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {
        }
    }

    fun fetchGenres(): List<String> {
        val html = fetchPage()
        val genreSection = html.substringAfter("name=\"genre\"", "")
        if (genreSection.isEmpty()) return emptyList()
        val options = Regex("""<option value="([^"]+)">""").findAll(genreSection.substringBefore("</select>"))
        return options.map { it.groupValues[1] }.toList()
    }

    fun uploadTrack(
        title: String,
        artist: String,
        genre: String,
        musicBytes: ByteArray,
        musicName: String,
        coverBytes: ByteArray?,
        coverName: String?
    ) {
        val html = fetchPage()
        val csrf = extractCsrfToken(html) ?: throw PurpleException("CSRF token not found")

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("csrf_token", csrf)
            .addFormDataPart("title", title)
            .addFormDataPart("artist", artist)
            .addFormDataPart("genre", genre)
            .addFormDataPart("upload", "")
            .addFormDataPart("music", musicName, musicBytes.toRequestBody("audio/*".toMediaType()))

        if (coverBytes != null && coverName != null) {
            bodyBuilder.addFormDataPart("cover", coverName, coverBytes.toRequestBody("image/*".toMediaType()))
        }

        val request = Request.Builder()
            .url(indexUrl())
            .post(bodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PurpleException("Upload failed: ${response.code}")
            val resHtml = response.body?.string() ?: ""
            if (resHtml.contains("Format audio non autorisé")) throw PurpleException("Format audio non autorisé.")
            if (resHtml.contains("Patientez")) throw PurpleException("Veuillez patienter avant un nouvel upload.")
        }
    }

    private fun parseTracks(array: JSONArray): List<Track> {
        val tracks = ArrayList<Track>(array.length())
        for (i in 0 until array.length()) {
            tracks.add(Track.fromJson(array.getJSONObject(i)))
        }
        return tracks
    }

    private fun parsePlaylistsFromHtml(html: String): List<Playlist> {
        val regex = Regex(
            """<h3[^>]*>([^<]+)</h3>[\s\S]*?playPlaylist\('([^']*)',\s*'?(\d+)'?\)""",
        )
        val results = mutableListOf<Playlist>()
        regex.findAll(html).forEach { match ->
            val name = match.groupValues[1].trim()
            val ids = match.groupValues[2].split(',').mapNotNull { it.trim().toIntOrNull() }
            val id = match.groupValues[3].toIntOrNull() ?: return@forEach
            if (name.isNotEmpty()) {
                results.add(Playlist(id = id, name = name, songIds = ids, creatorName = ""))
            }
        }
        return results.distinctBy { it.id }
    }

    private fun extractCsrfToken(html: String): String? {
        val regex = Regex("""name="csrf_token"\s+value="([^"]+)"""")
        return regex.find(html)?.groupValues?.get(1)
    }

    private fun extractSiteName(html: String): String? {
        val logoRegex = Regex("""<div class="logo"[^>]*>([^<]+)</div>""")
        return logoRegex.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractJsonArray(html: String, variableName: String): JSONArray? {
        val marker = "const $variableName"
        val startIndex = html.indexOf(marker)
        if (startIndex < 0) return null
        val arrayStart = html.indexOf('[', startIndex)
        if (arrayStart < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in arrayStart until html.length) {
            val c = html[i]
            if (escape) {
                escape = false
                continue
            }
            when {
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '[' -> depth++
                !inString && c == ']' -> {
                    depth--
                    if (depth == 0) {
                        return try {
                            JSONArray(html.substring(arrayStart, i + 1))
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
        return null
    }
}

class PurpleException(message: String) : Exception(message)
