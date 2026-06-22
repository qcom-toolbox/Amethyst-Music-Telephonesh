package com.qualcomm_toolbox.amethyst.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class OfflineLibrary(context: Context) {
    private val rootDir = File(context.filesDir, "offline").apply { mkdirs() }
    private val musicDir = File(rootDir, "music").apply { mkdirs() }
    private val coverDir = File(rootDir, "covers").apply { mkdirs() }
    private val indexFile = File(rootDir, "index.json")

    private var entries: MutableList<OfflineEntry> = loadIndex().toMutableList()
    
    // Cache for faster lookup
    private var entryMap = ConcurrentHashMap<Pair<String, Int>, OfflineEntry>()
    private val coverUriCache = ConcurrentHashMap<Pair<String, Int>, Uri?>()
    private val musicUriCache = ConcurrentHashMap<Pair<String, Int>, Uri?>()

    init {
        rebuildMap()
    }

    private fun rebuildMap() {
        val newMap = ConcurrentHashMap<Pair<String, Int>, OfflineEntry>()
        entries.forEach { newMap[it.serverUrl to it.track.id] = it }
        entryMap = newMap
        coverUriCache.clear()
        musicUriCache.clear()
    }

    fun getTracks(serverUrl: String): List<Track> =
        entries.filter { it.serverUrl == serverUrl }.map { it.track }

    fun getDownloadedIds(serverUrl: String): List<Int> =
        entries.filter { it.serverUrl == serverUrl }.map { it.track.id }

    fun isDownloaded(serverUrl: String, trackId: Int): Boolean =
        entryMap.containsKey(serverUrl to trackId)

    fun hasTracksForServer(serverUrl: String?): Boolean =
        serverUrl != null && entries.any { it.serverUrl == serverUrl }

    fun getMusicUri(serverUrl: String, trackId: Int): Uri? {
        val key = serverUrl to trackId
        musicUriCache[key]?.let { return it }
        
        val entry = entryMap[key] ?: return null
        val file = File(rootDir, entry.musicRelativePath)
        val uri = if (file.exists()) Uri.fromFile(file) else null
        if (uri != null) musicUriCache[key] = uri
        return uri
    }

    fun getCoverUri(serverUrl: String, trackId: Int): Uri? {
        val key = serverUrl to trackId
        // We use a specific check for null as we want to cache negative results too
        if (coverUriCache.containsKey(key)) return coverUriCache[key]
        
        val entry = entryMap[key] ?: return null
        val rel = entry.coverRelativePath ?: return null
        val file = File(rootDir, rel)
        val uri = if (file.exists()) Uri.fromFile(file) else null
        coverUriCache[key] = uri
        return uri
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
        rebuildMap()
        saveIndex()
    }

    @Synchronized
    fun remove(serverUrl: String, trackId: Int) {
        val entry = entryMap[serverUrl to trackId] ?: return
        File(rootDir, entry.musicRelativePath).delete()
        entry.coverRelativePath?.let { File(rootDir, it).delete() }
        entries.remove(entry)
        rebuildMap()
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
        rebuildMap()
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
