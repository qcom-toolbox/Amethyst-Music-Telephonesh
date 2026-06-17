package com.qualcomm_toolbox.amethyst.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

class ServerPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_SERVER, value?.let { normalizeServerUrl(it) }).apply()
        }

    var savedUsername: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) {
            prefs.edit().putString(KEY_USERNAME, value).apply()
        }

    var isAdmin: Boolean
        get() = prefs.getBoolean(KEY_IS_ADMIN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ADMIN, value).apply()

    var adminModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADMIN_MODE_ENABLED, value).apply()

    var backgroundColor: Long
        get() = prefs.getLong(KEY_BG_COLOR, 0xFF0F0C1D)
        set(value) = prefs.edit().putLong(KEY_BG_COLOR, value).apply()

    var useHarmony: Boolean
        get() = prefs.getBoolean(KEY_USE_HARMONY, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_HARMONY, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "fr") ?: "fr"
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
        }

    /** Map of Genre -> Play count */
    var recentGenrePlays: Map<String, Int>
        get() {
            val raw = prefs.getString(KEY_GENRE_PLAYS, null) ?: return emptyMap()
            return raw.split(";").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
            }.toMap()
        }
        set(value) {
            val raw = value.entries.joinToString(";") { "${it.key}:${it.value}" }
            prefs.edit().putString(KEY_GENRE_PLAYS, raw).apply()
        }

    fun recordGenrePlay(genre: String) {
        val current = recentGenrePlays.toMutableMap()
        current[genre] = (current[genre] ?: 0) + 1
        recentGenrePlays = current
    }

    val hasServer: Boolean get() = !serverUrl.isNullOrBlank()

    /** When true, accepts self-signed / invalid HTTPS certs (needed on some Android 6 devices). */
    var trustAllCertificates: Boolean
        get() = prefs.getBoolean(
            KEY_TRUST_ALL_CERTS,
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.M,
        )
        set(value) {
            prefs.edit().putBoolean(KEY_TRUST_ALL_CERTS, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "amethyst_prefs"
        private const val KEY_SERVER = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_TRUST_ALL_CERTS = "trust_all_certs"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_ADMIN_MODE_ENABLED = "admin_mode_enabled"
        private const val KEY_GENRE_PLAYS = "genre_plays"
        private const val KEY_BG_COLOR = "bg_color"
        private const val KEY_USE_HARMONY = "use_harmony"

        fun normalizeServerUrl(raw: String): String {
            var url = raw.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            while (url.endsWith('/')) {
                url = url.dropLast(1)
            }
            return url
        }
    }
}
