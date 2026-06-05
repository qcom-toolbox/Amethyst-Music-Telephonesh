package com.qualcomm_toolbox.amethyst.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OfflineLibrary(context: Context) {
    private val rootDir = File(context.filesDir, "offline").apply { mkdirs() }
    private val musicDir = File(rootDir, "music").apply { mkdirs() }
    private val coverDir = File(rootDir, "covers").apply { mkdirs() }
    private val indexFile = File(rootDir, "index.json")

    private var entries: MutableList<OfflineEntry> = loadIndex().toMutableList()

    fun getTracks(serverUrl: String): List<Track> =
        entries.filter { it.serverUrl == serverUrl }.map { it.track }

    fun getDownloadedIds(serverUrl: String): List<Int> =
        entries.filter { it.serverUrl == serverUrl }.map { it.track.id }

    fun isDownloaded(serverUrl: String, trackId: Int): Boolean =
        entries.any { it.serverUrl == serverUrl && it.track.id == trackId }

    fun hasTracksForServer(serverUrl: String?): Boolean =
        serverUrl != null && entries.any { it.serverUrl == serverUrl }

    fun getMusicUri(serverUrl: String, trackId: Int): Uri? {
        val entry = entries.find { it.serverUrl == serverUrl && it.track.id == trackId } ?: return null
        val file = File(rootDir, entry.musicRelativePath)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun getCoverUri(serverUrl: String, trackId: Int): Uri? {
        val entry = entries.find { it.serverUrl == serverUrl && it.track.id == trackId } ?: return null
        val rel = entry.coverRelativePath ?: return null
        val file = File(rootDir, rel)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun musicFileFor(trackId: Int, extension: String): File {
        val ext = extension.lowercase().removePrefix(".")
        return File(musicDir, "$trackId.$ext")
    }

    fun coverFileFor(trackId: Int, coverName: String): File {
        val ext = coverName.substringAfterLast('.', "png")
        return File(coverDir, "$trackId.$ext")
    }

    @Synchronized
    fun addEntry(
        serverUrl: String,
        track: Track,
        musicRelativePath: String,
        coverRelativePath: String?,
    ) {
        entries.removeAll { it.serverUrl == serverUrl && it.track.id == track.id }
        entries.add(
            OfflineEntry(
                serverUrl = serverUrl,
                track = track,
                musicRelativePath = musicRelativePath,
                coverRelativePath = coverRelativePath,
            ),
        )
        saveIndex()
    }

    @Synchronized
    fun remove(serverUrl: String, trackId: Int) {
        val entry = entries.find { it.serverUrl == serverUrl && it.track.id == trackId } ?: return
        File(rootDir, entry.musicRelativePath).delete()
        entry.coverRelativePath?.let { File(rootDir, it).delete() }
        entries.remove(entry)
        saveIndex()
    }

    @Synchronized
    fun removeAllForServer(serverUrl: String) {
        entries.filter { it.serverUrl == serverUrl }.toList().forEach {
            remove(serverUrl, it.track.id)
        }
    }

    @Synchronized
    fun reload() {
        entries = loadIndex().toMutableList()
    }

    private fun loadIndex(): List<OfflineEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val array = JSONObject(indexFile.readText()).getJSONArray("entries")
            buildList {
                for (i in 0 until array.length()) {
                    add(OfflineEntry.fromJson(array.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveIndex() {
        val json = JSONObject().put(
            "entries",
            JSONArray().apply {
                entries.forEach { put(it.toJson()) }
            },
        )
        indexFile.writeText(json.toString())
    }

    private data class OfflineEntry(
        val serverUrl: String,
        val track: Track,
        val musicRelativePath: String,
        val coverRelativePath: String?,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("serverUrl", serverUrl)
            .put("track", track.toJson())
            .put("musicRelativePath", musicRelativePath)
            .put("coverRelativePath", coverRelativePath)

        companion object {
            fun fromJson(obj: JSONObject): OfflineEntry = OfflineEntry(
                serverUrl = obj.getString("serverUrl"),
                track = Track.fromJson(obj.getJSONObject("track")),
                musicRelativePath = obj.getString("musicRelativePath"),
                coverRelativePath = obj.optString("coverRelativePath").takeIf { it.isNotBlank() },
            )
        }
    }
}

private fun Track.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("filename", filename)
    .put("title", title)
    .put("artist", artist)
    .put("cover", cover)
    .put("genre", genre)
    .put("play_count", playCount)
    .put("duration", duration)
