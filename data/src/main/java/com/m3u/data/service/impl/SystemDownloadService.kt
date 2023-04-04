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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SystemDownloadService @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadService {
    private var isRegister: Boolean = false
    private val downloadManager: DownloadManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val downloadTaskStatusObservers = mutableMapOf<Long, DownloadTaskStatusObserver>()
    private val downloadTaskProcessObservers = mutableMapOf<Long, DownloadTaskProcessObserver>()

    private var processJobs = mutableMapOf<Long, Job>()


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
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setDescription(description)
            .setNotificationVisibility(visibility)
            .setDestinationInExternalPublicDir(dirType, subPath)
        return downloadManager.enqueue(request)
    }

    override fun getStatus(downloadId: Long): Int = try {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use {
            val toFirst = it.moveToFirst()
            if (toFirst) it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            else DownloadManager.STATUS_FAILED
        }
    } catch (ignored: Exception) {
        DownloadManager.STATUS_FAILED
    }

    override fun getProcess(downloadId: Long): Int = try {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use {
            if (it.moveToFirst()) {
                val downloaded =
                    it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total =
                    it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                (downloaded * 100 / total).toInt()
            } else 0
        }
    } catch (ignored: Exception) {
        0
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

    override suspend fun addProcessObserver(
        downloadId: Long,
        observer: DownloadTaskProcessObserver
    ) {
        coroutineScope {
            val job = processJobs[downloadId]
            job?.cancel()
            processJobs[downloadId] = launch {
                while (true) {
                    val process = getProcess(downloadId)
                    downloadTaskProcessObservers[downloadId]?.invoke(process)
                    if (process == 100) {
                        processJobs[downloadId]?.cancel()
                    }
                    delay(50)
                }
            }
            downloadTaskProcessObservers[downloadId] = observer
        }
    }

    override fun removeProcessObserver(downloadId: Long): DownloadTaskProcessObserver? =
        downloadTaskProcessObservers[downloadId]

    private val downloadTasksBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                val status = getStatus(downloadId)
                downloadTaskStatusObservers[downloadId]?.invoke(status)
            }
        }
    }

    override fun register() {
        if (!isRegister) {
            context.registerReceiver(
                downloadTasksBroadcastReceiver,
                IntentFilter().apply {
                    addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                    addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
                }
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