package com.qualcomm_toolbox.amethyst.data

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PurpleClient(
    private var baseUrl: String,
    trustAllCerts: Boolean = false,
    private val cookieJar: PersistentCookieJar? = null,
) {
    private var trustAllCerts: Boolean = trustAllCerts
    private var client: OkHttpClient = buildHttpClient()
    private var currentUsername: String? = null
    private var currentPassword: String? = null

    val okHttpClient: OkHttpClient get() = client

    fun setCredentials(user: String?, pass: String?) {
        currentUsername = user
        currentPassword = pass
    }

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
        if (cookieJar != null) builder.cookieJar(cookieJar)
        if (trustAllCerts) UnsafeSsl.applyTo(builder)
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

    fun hasValidSession(): Boolean = cookieJar?.hasCookies() ?: false

    private fun apiUrl(action: String): HttpUrl {
        return "$baseUrl/api.php".toHttpUrl()
            .newBuilder()
            .addQueryParameter("action", action)
            .build()
    }

    fun musicUrl(trackId: Int): String = apiUrl("stream").newBuilder().addQueryParameter("q", trackId.toString()).build().toString()

    fun coverUrl(trackId: Int): String = apiUrl("cover").newBuilder().addQueryParameter("q", trackId.toString()).build().toString()

    private fun postRequest(action: String, params: Map<String, String> = emptyMap()): String {
        val body = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
            currentUsername?.let { add("username", it) }
            currentPassword?.let { add("password", it) }
        }.build()

        val request = Request.Builder()
            .url(apiUrl(action))
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PurpleException("HTTP ${response.code}")
            }
            return response.body?.string() ?: throw PurpleException("Empty response")
        }
    }

    fun validateServer(): String {
        try {
            val json = postRequest("list")
            if (json.startsWith("[")) return "Amethyst Music"
        } catch (_: Exception) {}
        throw PurpleException("Serveur Amethyst non détecté")
    }

    fun login(username: String, password: String): Boolean {
        val resp = postRequest("login", mapOf("username" to username, "password" to password))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Login failed"))
        }
        // Support both boolean (PHP json_encode) and integer (MySQL TINYINT)
        return json.optBoolean("is_admin", json.optInt("is_admin", 0) == 1)
    }

    fun register(username: String, password: String): Boolean {
        val resp = postRequest("register", mapOf("username" to username, "password" to password))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Registration failed"))
        }
        return false // New users are likely not admins by default from register action in PHP
    }

    fun fetchTracks(): List<Track> {
        val resp = postRequest("list")
        val array = JSONArray(resp)
        return (0 until array.length()).map { i ->
            Track.fromJson(array.getJSONObject(i))
        }
    }

    fun incrementPlay(trackId: Int) {
        try {
            postRequest("increment_play", mapOf("track_id" to trackId.toString()))
        } catch (_: Exception) {}
    }

    fun fetchGenres(): List<String> = listOf(
        "Autre", "Phonk/Funk", "Rap", "Pop", "Rock", "Electro",
        "Hyperpop", "Nightcore"
    )

    fun uploadTrack(
        title: String, artist: String, genre: String,
        musicBytes: ByteArray, musicName: String,
        coverBytes: ByteArray?, coverName: String?
    ) {
        val url = apiUrl("upload")

        // Build multipart body with text fields FIRST for better server compatibility
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("title", title)
            .addFormDataPart("artist", artist)
            .addFormDataPart("genre", genre)

        currentUsername?.let { builder.addFormDataPart("username", it) }
        currentPassword?.let { builder.addFormDataPart("password", it) }

        // Files LAST
        builder.addFormDataPart(
            "music",
            musicName,
            musicBytes.toRequestBody("application/octet-stream".toMediaType())
        )

        if (coverBytes != null && coverName != null) {
            builder.addFormDataPart(
                "cover",
                coverName,
                coverBytes.toRequestBody("image/*".toMediaType())
            )
        }

        val body = builder.build()
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PurpleException("Upload failed (HTTP ${response.code})")
            val bodyStr = response.body?.string() ?: ""
            if (bodyStr.contains("\"status\":\"error\"")) {
                val msg = JSONObject(bodyStr).optString("message", "Upload error")
                throw PurpleException(msg)
            }
        }
    }

    fun editTrack(
        trackId: Int,
        title: String,
        artist: String,
        genre: String,
        newCoverBytes: ByteArray?,
        newCoverName: String?
    ) {
        val url = apiUrl("edit_track")
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("track_id", trackId.toString())
            .addFormDataPart("title", title)
            .addFormDataPart("artist", artist)
            .addFormDataPart("new_genre", genre)

        currentUsername?.let { builder.addFormDataPart("username", it) }
        currentPassword?.let { builder.addFormDataPart("password", it) }

        if (newCoverBytes != null && newCoverName != null) {
            builder.addFormDataPart(
                "new_cover",
                newCoverName,
                newCoverBytes.toRequestBody("image/*".toMediaType())
            )
        }

        val body = builder.build()
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PurpleException("Edit failed (HTTP ${response.code})")
            val bodyStr = response.body?.string() ?: ""
            if (bodyStr.contains("\"status\":\"error\"")) {
                val msg = JSONObject(bodyStr).optString("message", "Edit error")
                throw PurpleException(msg)
            }
        }
    }

    fun deleteTrack(trackId: Int) {
        val resp = postRequest("delete_track", mapOf("track_id" to trackId.toString()))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Delete failed"))
        }
    }

    fun fetchPlaylists(): List<Playlist> {
        val resp = postRequest("playlists")
        val array = JSONArray(resp)
        return (0 until array.length()).map { i ->
            Playlist.fromJson(array.getJSONObject(i))
        }
    }

    fun fetchPlaylistTracks(ids: List<Int>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val allTracks = fetchTracks().associateBy { it.id }
        return ids.mapNotNull { allTracks[it] }
    }

    fun createPlaylist(name: String) {
        val resp = postRequest("playlist_create", mapOf("name" to name))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Failed to create playlist"))
        }
    }

    fun addToPlaylist(playlistId: Int, trackId: Int) {
        val resp = postRequest("playlist_mod", mapOf(
            "playlist_id" to playlistId.toString(),
            "mode" to "add",
            "track_id" to trackId.toString()
        ))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Failed to add to playlist"))
        }
    }

    fun removeFromPlaylist(playlistId: Int, trackId: Int) {
        val resp = postRequest("playlist_mod", mapOf(
            "playlist_id" to playlistId.toString(),
            "mode" to "remove",
            "track_id" to trackId.toString()
        ))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Failed to remove from playlist"))
        }
    }

    fun deletePlaylist(playlistId: Int) {
        val resp = postRequest("playlist_mod", mapOf(
            "playlist_id" to playlistId.toString(),
            "mode" to "delete"
        ))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Failed to delete playlist"))
        }
    }
}

class PurpleException(message: String) : Exception(message)
