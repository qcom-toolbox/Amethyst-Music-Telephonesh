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
        fun fromJson(obj: JSONObject): Track = Track(
            id = obj.optInt("id"),
            filename = obj.optString("filename", ""),
            title = HtmlEntities.decode(obj.optString("title")).ifBlank { "Unknown" },
            artist = HtmlEntities.decode(obj.optString("artist")).ifBlank { "Unknown" },
            cover = obj.optString("cover").ifBlank { "default.png" },
            genre = HtmlEntities.decode(obj.optString("genre")).ifBlank { "Autre" },
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
            )
        }
    }
}
