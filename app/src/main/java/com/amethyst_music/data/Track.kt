package com.amethyst_music.data

import org.json.JSONObject

data class Track(
    val id: Int,
    val filename: String,
    val title: String,
    val artist: String,
    val cover: String,
    val genre: String,
    val playCount: Int,
    val duration: Int,
    val uploaderId: Int,
    val albumId: Int? = null,
    val album: String? = null,
) {
    companion object {
        /**
         * Genre a track falls back to when the server sends it without one. It's a real value
         * the backend's own `genres` table uses (not a sentinel), so it can show up either
         * because the track is genuinely tagged "other" or because it isn't tagged at all —
         * both cases behave the same everywhere in the app, including the Settings ignore list,
         * where it's displayed via R.string.genre_other so it reads in the user's language.
         */
        const val UNTAGGED_GENRE = "Autre"

        fun fromJson(obj: JSONObject): Track = Track(
            id = obj.optInt("id"),
            filename = obj.optString("filename", ""),
            title = HtmlEntities.decode(obj.optString("title")).ifBlank { "Unknown" },
            artist = HtmlEntities.decode(obj.optString("artist")).ifBlank { "Unknown" },
            cover = obj.optString("cover").ifBlank { "default.png" },
            genre = HtmlEntities.decode(obj.optString("genre")).ifBlank { UNTAGGED_GENRE },
            playCount = obj.optInt("play_count", 0),
            duration = obj.optInt("duration", 0),
            uploaderId = obj.optInt("uploader_id", 0),
            albumId = if (obj.isNull("album_id")) null else obj.optInt("album_id"),
            album = if (obj.isNull("album")) null else HtmlEntities.decode(obj.optString("album")).ifBlank { null },
        )
    }
}

data class Playlist(
    val id: Int,
    val name: String,
    val songIds: List<Int>,
    val creatorName: String,
    val isPublic: Boolean = true,
) {
    companion object {
        fun fromJson(obj: JSONObject): Playlist {
            val ids = obj.optString("song_ids", "")
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
            return Playlist(
                id = obj.getInt("id"),
                name = HtmlEntities.decode(obj.getString("name")),
                songIds = ids,
                creatorName = HtmlEntities.decode(obj.optString("creator").ifBlank { obj.optString("username", "") }),
                // MySQL returns TINYINT(1) as an integer over JSON, not a JSON boolean.
                isPublic = obj.optBoolean("is_public", obj.optInt("is_public", 1) == 1),
            )
        }
    }
}
