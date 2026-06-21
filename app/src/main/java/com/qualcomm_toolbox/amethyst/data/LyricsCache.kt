package com.qualcomm_toolbox.amethyst.data

import android.content.Context
import java.io.File

class LyricsCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "lyrics")
    private val memoryCache = mutableMapOf<Int, String>()

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun get(trackId: Int): String? {
        // Try memory first
        memoryCache[trackId]?.let { return it }

        // Try disk
        val file = File(cacheDir, trackId.toString())
        return if (file.exists()) {
            val content = file.readText()
            memoryCache[trackId] = content
            content
        } else {
            null
        }
    }

    fun put(trackId: Int, lyrics: String) {
        memoryCache[trackId] = lyrics
        val file = File(cacheDir, trackId.toString())
        try {
            file.writeText(lyrics)
        } catch (_: Exception) {}
    }

    fun clear() {
        memoryCache.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
