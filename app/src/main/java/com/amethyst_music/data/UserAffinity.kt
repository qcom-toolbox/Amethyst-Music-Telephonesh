package com.amethyst_music.data

import org.json.JSONObject

/**
 * The user's taste profile from action=user_affinity: which genres, artists and albums they
 * tend to listen to, weighted server-side by recency of plays plus a stronger fixed bonus for
 * anything they've put in a playlist (see compute_user_affinity in api.php). Fetched once per
 * session and kept in memory — it doesn't meaningfully change moment to moment. Anonymous or
 * brand-new users get [EMPTY] (server returns empty objects, not an error).
 */
data class UserAffinity(
    val genre: Map<String, Double>,
    val artist: Map<String, Double>,
    val album: Map<Int, Double>,
) {
    companion object {
        val EMPTY = UserAffinity(emptyMap(), emptyMap(), emptyMap())

        private fun JSONObject.toDoubleMap(): Map<String, Double> =
            keys().asSequence().associateWith { optDouble(it, 0.0) }

        fun fromJson(obj: JSONObject): UserAffinity {
            val genre = obj.optJSONObject("genre")?.toDoubleMap() ?: emptyMap()
            val artist = obj.optJSONObject("artist")?.toDoubleMap() ?: emptyMap()
            val album = obj.optJSONObject("album")?.toDoubleMap()
                ?.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }?.toMap() ?: emptyMap()
            return UserAffinity(genre, artist, album)
        }
    }
}
