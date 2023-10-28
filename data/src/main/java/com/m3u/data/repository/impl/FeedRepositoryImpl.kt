package com.m3u.data.repository.impl

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.execute
import com.m3u.core.architecture.sandBox
import com.m3u.core.util.basic.startsWithAny
import com.m3u.core.util.belong
import com.m3u.core.util.readFileName
import com.m3u.core.util.readFileContent
import com.m3u.core.wrapper.ProgressResource
import com.m3u.core.wrapper.emitException
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitProgress
import com.m3u.core.wrapper.emitResource
import com.m3u.data.database.dao.FeedDao
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import com.m3u.data.parser.PlaylistParser
import com.m3u.data.parser.impl.toLive
import com.m3u.data.repository.FeedRepository
import com.m3u.i18n.R.string
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val liveDao: LiveDao,
    @Logger.Ui private val logger: Logger,
    private val client: OkHttpClient,
    @PlaylistParser.Experimental private val parser: PlaylistParser,
    @ApplicationContext private val context: Context
) : FeedRepository {

    override fun subscribe(
        title: String,
        url: String,
        strategy: Int
    ): Flow<ProgressResource<Unit>> = flow {
        try {
            val actualUrl = url.actualUrl()
            if (actualUrl == null) {
                emitMessage("wrong url")
                return@flow
            }
            val lives = when {
                url.isNetworkUrl -> acquireNetwork(url)
                url.isAndroidUrl -> acquireAndroid(url)
                else -> emptyList()
            }

            val feed = Feed(title, actualUrl)
            feedDao.insert(feed)

            merge(
                prev = liveDao.getByFeedUrl(url),
                lives = lives,
                strategy = strategy
            ) { progress ->
                emitProgress(progress)
            }

            emitResource(Unit)
        } catch (e: FileNotFoundException) {
            error(context.getString(string.data_error_file_not_found))
        } catch (e: Exception) {
            logger.log(e)
            emitException(e)
        }
    }

    private suspend inline fun merge(
        prev: List<Live>,
        lives: List<Live>,
        @FeedStrategy strategy: Int,
        onProgress: (Int) -> Unit
    ) {
        val skippedUrls = mutableListOf<String>()
        val grouped by lazy {
            prev.groupBy { it.favourite }.withDefault { emptyList() }
        }
        val invalidate = when (strategy) {
            FeedStrategy.ALL -> prev
            FeedStrategy.SKIP_FAVORITE -> grouped.getValue(false)
            else -> emptyList()
        }
        invalidate.forEach { live ->
            if (live belong lives) {
                skippedUrls += live.url
            } else {
                liveDao.deleteByUrl(live.url)
            }
        }
        val existedUrls = when (strategy) {
            FeedStrategy.ALL -> skippedUrls
            FeedStrategy.SKIP_FAVORITE -> grouped
                .getValue(true)
                .map { it.url } + skippedUrls
            else -> emptyList()
        }
        var progress = 0
        lives
            .filterNot { it.url in existedUrls }
            .forEach {
                liveDao.insert(it)
                progress++
                onProgress(progress)
            }
    }

    private suspend fun acquireNetwork(url: String): List<Live> {
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val input = response.body?.byteStream()
        return input?.use { parse(url, it) } ?: emptyList()
    }

    private suspend fun acquireAndroid(url: String): List<Live> {
        val uri = Uri.parse(url)
        val input = context.contentResolver.openInputStream(uri)
        return input?.use { parse(url, it) } ?: emptyList()
    }

    override fun observeAll(): Flow<List<Feed>> = logger.execute {
        feedDao.observeAll()
    } ?: flow { }

    override fun observe(url: String): Flow<Feed?> = logger.execute {
        feedDao.observeByUrl(url)
    } ?: flow { }

    override suspend fun get(url: String): Feed? = logger.execute {
        feedDao.getByUrl(url)
    }

    override suspend fun unsubscribe(url: String): Feed? = logger.execute {
        val feed = feedDao.getByUrl(url)
        liveDao.deleteByFeedUrl(url)
        feed?.also {
            feedDao.delete(it)
        }
    }

    private suspend fun parse(uri: String, input: InputStream): List<Live> = logger.execute {
        parser.execute(input).map { it.toLive(uri) }
    } ?: emptyList()

    override suspend fun rename(url: String, target: String) = logger.sandBox {
        feedDao.rename(url, target)
    }

    private val String.isNetworkUrl: Boolean get() = this.startsWithAny("http://", "https://")
    private val String.isAndroidUrl: Boolean get() = this.startsWithAny("file://", "content://")

    private suspend fun String.actualUrl(): String? {
        return if (isNetworkUrl) this
        else if (isAndroidUrl) {
            val uri = Uri.parse(this) ?: return null
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val filename = uri.readFileName(resolver) ?: filenameWithTimezone
                val content = uri.readFileContent(resolver).orEmpty()
                val file = File(context.filesDir, filename)
                file.writeText(content)
                Uri.decode(file.toUri().toString())
            }
        } else null
    }

    private val filenameWithTimezone: String get() = "File_${System.currentTimeMillis()}"
}
