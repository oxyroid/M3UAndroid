package com.m3u.data.service.internal

import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun DownloadManager.observeDownloads(
    @Download.State vararg states: Int
): Flow<List<Download>> = callbackFlow {
    val listener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            trySendBlocking(
                downloadManager.getDownloads(*states)
            )
        }

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            trySendBlocking(
                downloadManager.getDownloads(*states)
            )
        }

        override fun onDownloadRemoved(
            downloadManager: DownloadManager,
            download: Download
        ) {
            trySendBlocking(
                downloadManager.getDownloads(*states)
            )
        }
    }
    addListener(listener)
    awaitClose {
        removeListener(listener)
    }
}

private fun DownloadManager.getDownloads(@Download.State vararg states: Int): List<Download> = try {
    buildList {
        val cursor = downloadIndex.getDownloads(*states)
        while (cursor.moveToNext()) {
            add(cursor.download)
        }
        cursor.close()
    }
} catch (e: Exception) {
    emptyList()
}