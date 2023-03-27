package com.m3u.data.service.impl

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.m3u.data.service.DownloadService
import com.m3u.data.service.DownloadTaskProcessObserver
import com.m3u.data.service.DownloadTaskStatusObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DownloadServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadService {
    private var isRegister: Boolean = false
    private val downloadManager: DownloadManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val downloadTaskStatusObservers = mutableMapOf<Long, DownloadTaskStatusObserver>()
    private val downloadTaskProcessObservers = mutableMapOf<Long, DownloadTaskProcessObserver>()

    override fun enqueueDownloadTask(
        title: String,
        description: String,
        uri: Uri,
        subPath: String,
        visibility: Int,
        dirType: String
    ): Long {
        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription(description)
            .setNotificationVisibility(visibility)
            .setDestinationInExternalPublicDir(dirType, subPath)
        return downloadManager.enqueue(request)
    }

    override fun getStatus(downloadId: Long): Int = try {
        val query = DownloadManager.Query()
            .setFilterById(downloadId)
        downloadManager.query(query).use {
            if (it.moveToFirst()) it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            else DownloadManager.STATUS_FAILED
        }
    } catch (ignored: Exception) {
        DownloadManager.STATUS_FAILED
    }

    override fun getProcess(downloadId: Long): Int = try {
        val query = DownloadManager.Query()
            .setFilterById(downloadId)
        downloadManager.query(query).use {
            if (it.moveToFirst()) {
                val bytesDownloaded =
                    it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal =
                    it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                bytesDownloaded * 100 / bytesTotal
            } else -1
        }
    } catch (ignored: Exception) {
        -1
    }

    override fun addStatusObserver(
        downloadId: Long,
        observer: DownloadTaskStatusObserver
    ) {
        downloadTaskStatusObservers[downloadId] = observer
    }

    override fun removeStatusObserver(downloadId: Long): DownloadTaskStatusObserver? {
        return downloadTaskStatusObservers.remove(downloadId)
    }

    override fun addProcessObserver(
        downloadId: Long,
        observer: DownloadTaskProcessObserver
    ) {
        downloadTaskProcessObservers[downloadId] = observer
    }

    override fun removeProcessObserver(downloadId: Long): DownloadTaskProcessObserver? =
        downloadTaskProcessObservers[downloadId]

    private val downloadTasksBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                val status = getStatus(downloadId)
                downloadTaskStatusObservers[downloadId]?.invoke(status)
                val process = when (status) {
                    DownloadManager.STATUS_PENDING -> 0
                    DownloadManager.STATUS_RUNNING -> getProcess(downloadId)
                    DownloadManager.STATUS_SUCCESSFUL -> 100
                    DownloadManager.STATUS_FAILED -> -1
                    else -> -1
                }
                downloadTaskProcessObservers[downloadId]?.invoke(process)
            }
        }
    }

    override fun register() {
        if (!isRegister) {
            context.registerReceiver(
                downloadTasksBroadcastReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),

            )
            isRegister = true
        }
    }

    override fun unregister() {
        if (isRegister) {
            context.unregisterReceiver(downloadTasksBroadcastReceiver)
            isRegister = false
        }
    }
}