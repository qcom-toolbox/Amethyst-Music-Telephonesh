package com.qualcomm_toolbox.amethyst.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class TrackDownloader {
    suspend fun download(
        httpClient: OkHttpClient,
        purple: PurpleClient,
        track: Track,
        serverUrl: String,
        library: OfflineLibrary,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val ext = track.filename.substringAfterLast('.', "mp3")
        val musicFile = library.musicFileFor(track.id, ext)
        downloadToFile(
            client = httpClient,
            url = purple.musicUrl(track.id),
            dest = musicFile,
            onProgress = onProgress,
        )
        val coverFile = library.coverFileFor(track.id, track.cover)
        try {
            downloadToFile(
                client = httpClient,
                url = purple.coverUrl(track.id),
                dest = coverFile,
                onProgress = {},
            )
        } catch (_: Exception) {
            coverFile.delete()
        }
        library.addEntry(
            serverUrl = serverUrl,
            track = track,
            musicRelativePath = "music/${track.id}.$ext",
            coverRelativePath = if (coverFile.exists() && coverFile.length() > 0) {
                "covers/${track.id}.${track.cover.substringAfterLast('.', "png")}"
            } else {
                null
            },
        )
    }

    private fun downloadToFile(
        client: OkHttpClient,
        url: String,
        dest: File,
        onProgress: (Float) -> Unit,
    ) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PurpleException("Téléchargement échoué (${response.code})")
            }
            val body = response.body ?: throw PurpleException("Fichier vide")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                BufferedOutputStream(FileOutputStream(dest)).use { output ->
                    val buffer = ByteArray(65536) // Use a larger 64KB buffer
                    var downloaded = 0L
                    var read: Int
                    var lastProgressUpdate = 0L
                    
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        
                        if (total > 0) {
                            val now = System.currentTimeMillis()
                            // Update progress at most every 200ms to avoid saturating the UI thread
                            if (now - lastProgressUpdate > 200) {
                                val progress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                onProgress(progress)
                                lastProgressUpdate = now
                            }
                        }
                    }
                }
            }
            onProgress(1f)
        }
    }
}
