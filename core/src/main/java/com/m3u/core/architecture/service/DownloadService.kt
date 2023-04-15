@file:Suppress("unused")

package com.m3u.core.architecture.service

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment

typealias DownloadTaskStatusObserver = (Int) -> Unit
typealias DownloadTaskProcessObserver = (Int) -> Unit

interface DownloadService {
    fun enqueueDownloadTask(
        title: String,
        description: String,
        uri: Uri,
        subPath: String = subPath(uri),
        visibility: Int = DownloadManager.Request.VISIBILITY_VISIBLE,
        dirType: String = Environment.DIRECTORY_DOWNLOADS
    ): Long

    fun getStatus(downloadId: Long): Int
    fun getProcess(downloadId: Long): Int
    fun addStatusObserver(downloadId: Long, observer: DownloadTaskStatusObserver)
    fun removeStatusObserver(downloadId: Long): DownloadTaskStatusObserver?
    suspend fun addProcessObserver(downloadId: Long, observer: DownloadTaskProcessObserver)
    fun removeProcessObserver(downloadId: Long): DownloadTaskProcessObserver?

    fun register()
    fun unregister()

    companion object {
        fun subPath(uri: Uri) =
            "Investigation/File_${System.currentTimeMillis()}.${uri.lastPathSegment ?: "temp"}"
    }
}

fun DownloadService.enqueueDownloadTaskStatus(
    title: String,
    description: String,
    uri: Uri,
    filename: String,
    visibility: Int = DownloadManager.Request.VISIBILITY_VISIBLE,
    dirType: String = Environment.DIRECTORY_DOWNLOADS,
    observer: DownloadTaskStatusObserver
) {
    val downloadId = enqueueDownloadTask(title, description, uri, filename, visibility, dirType)
    addStatusObserver(downloadId, observer)
}

suspend fun DownloadService.enqueueDownloadTaskProcess(
    title: String,
    description: String,
    uri: Uri,
    subPath: String = DownloadService.subPath(uri),
    visibility: Int = DownloadManager.Request.VISIBILITY_VISIBLE,
    dirType: String = Environment.DIRECTORY_DOWNLOADS,
    observer: DownloadTaskProcessObserver
) {
    val downloadId = enqueueDownloadTask(title, description, uri, subPath, visibility, dirType)
    addProcessObserver(downloadId, observer)
}