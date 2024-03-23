package com.m3u.data.service.internal

import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun DownloadManager.allDownloadsAsFlow(): Flow<List<Download>> = callbackFlow {
    val listener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            trySendBlocking(
                downloadManager.getAllDownloads()
            )
        }

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            trySendBlocking(
                downloadManager.getAllDownloads()
            )
        }

        override fun onDownloadRemoved(
            downloadManager: DownloadManager,
            download: Download
        ) {
            trySendBlocking(
                downloadManager.getAllDownloads()
            )
        }
    }
    addListener(listener)
    awaitClose {
        removeListener(listener)
    }
}


private fun DownloadManager.getAllDownloads(): List<Download> {
    return try {
        buildList {
            val cursor = downloadIndex.getDownloads(
                Download.STATE_DOWNLOADING,
                Download.STATE_COMPLETED,
                Download.STATE_FAILED,
                Download.STATE_QUEUED,
                Download.STATE_RESTARTING,
                Download.STATE_REMOVING
            )
            while (cursor.moveToNext()) {
                add(cursor.download)
            }
            cursor.close()
        }
    } catch (e: Exception) {
        emptyList()
    }
}