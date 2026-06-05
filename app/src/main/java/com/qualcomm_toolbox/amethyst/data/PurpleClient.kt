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

    fun login(username: String, password: String) {
        val resp = postRequest("login", mapOf("username" to username, "password" to password))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Login failed"))
        }
    }

    fun register(username: String, password: String) {
        val resp = postRequest("register", mapOf("username" to username, "password" to password))
        val json = JSONObject(resp)
        if (json.optString("status") == "error") {
            throw PurpleException(json.optString("message", "Registration failed"))
        }
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

        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("title", title)
            .addFormDataPart("artist", artist)
            .addFormDataPart("genre", genre)
            .addFormDataPart("music", musicName, musicBytes.toRequestBody("audio/mpeg".toMediaType()))
            .apply {
                currentUsername?.let { addFormDataPart("username", it) }
                currentPassword?.let { addFormDataPart("password", it) }
                if (coverBytes != null && coverName != null) {
                    addFormDataPart("cover", coverName, coverBytes.toRequestBody("image/*".toMediaType()))
                }
            }
            .build()

        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PurpleException("Upload failed")
            val bodyStr = response.body?.string() ?: ""
            if (bodyStr.contains("\"status\":\"error\"")) throw PurpleException("Upload error")
        }
    }

    fun fetchPlaylists() = emptyList<Playlist>()
    fun fetchPlaylistTracks(ids: List<Int>) = emptyList<Track>()
}

class PurpleException(message: String) : Exception(message)
