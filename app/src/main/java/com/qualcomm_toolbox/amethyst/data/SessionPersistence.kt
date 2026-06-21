package com.qualcomm_toolbox.amethyst.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

class SessionPersistence(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var savedPassword: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(value) {
            prefs.edit().putString(KEY_PASSWORD, value).apply()
        }

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    fun saveCookies(serverUrl: String, cookies: List<Cookie>) {
        val host = serverUrl.toHttpUrlOrNull()?.host ?: return
        val array = JSONArray()
        cookies.forEach { array.put(it.toJson()) }
        prefs.edit().putString(cookieKey(host), array.toString()).apply()
    }

    fun loadCookies(serverUrl: String): List<Cookie> {
        val baseUrl = serverUrl.toHttpUrlOrNull() ?: return emptyList()
        val host = baseUrl.host
        val raw = prefs.getString(cookieKey(host), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    Cookie.fromJson(array.getJSONObject(i), baseUrl)?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearCookies(serverUrl: String?) {
        val host = serverUrl?.toHttpUrlOrNull()?.host ?: return
        prefs.edit().remove(cookieKey(host)).apply()
    }

    fun clearAllForServer(serverUrl: String?) {
        clearCookies(serverUrl)
    }

    private fun cookieKey(host: String) = "cookies_$host"

    companion object {
        private const val PREFS_NAME = "amethyst_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }

    private fun Cookie.toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("value", value)
        .put("domain", domain)
        .put("path", path)
        .put("expiresAt", expiresAt)
        .put("secure", secure)
        .put("httpOnly", httpOnly)
        .put("hostOnly", hostOnly)

    private fun Cookie.Companion.fromJson(obj: JSONObject, baseUrl: HttpUrl): Cookie? {
        return try {
            val header = buildString {
                append(obj.getString("name"))
                append('=')
                append(obj.getString("value"))
                append("; Path=")
                append(obj.optString("path", "/"))
                append("; Domain=")
                append(obj.getString("domain"))
                if (obj.optLong("expiresAt", 0L) > 0L) {
                    append("; Max-Age=")
                    append(((obj.getLong("expiresAt") - System.currentTimeMillis()) / 1000).coerceAtLeast(0))
                }
                if (obj.optBoolean("secure")) append("; Secure")
            }
            Cookie.parse(baseUrl, header)
        } catch (_: Exception) {
            null
        }
    }
}
