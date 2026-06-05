package com.qualcomm_toolbox.amethyst.data

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
) {
    companion object {
        fun fromJson(obj: JSONObject): Track = Track(
            id = obj.optInt("id"),
            filename = obj.optString("filename", ""),
            title = obj.optString("title", "Unknown"),
            artist = obj.optString("artist", "Unknown"),
            cover = obj.optString("cover", "default.png"),
            genre = obj.optString("genre", "Autre"),
            playCount = obj.optInt("play_count", 0),
            duration = obj.optInt("duration", 0),
            uploaderId = obj.optInt("uploader_id", 0)
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
                name = obj.getString("name"),
                songIds = ids,
                creatorName = obj.optString("username", ""),
            )
        }
    }
}
