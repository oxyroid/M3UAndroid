package com.m3u.data.repository.impl

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.core.net.toFile
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.util.collection.belong
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.database.dao.FeedDao
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import com.m3u.data.remote.parser.execute
import com.m3u.data.remote.parser.m3u.PlaylistParser
import com.m3u.data.remote.parser.m3u.toLive
import com.m3u.data.repository.FeedRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val liveDao: LiveDao,
    private val logger: Logger,
    configuration: Configuration,
    private val parser: PlaylistParser,
    @ApplicationContext private val context: Context
) : FeedRepository {
    private val connectTimeout by configuration.connectTimeout
    override fun subscribe(
        title: String,
        url: String,
        @FeedStrategy strategy: Int
    ): Flow<Resource<Unit>> = resourceFlow {
        try {
            val parent = url.findParentPath().orEmpty()
            val lives = when {
                url.startsWith("http://") || url.startsWith("https://") -> networkParse(url)
                url.startsWith("file://") || url.startsWith("content://") -> {
                    val uri = Uri.parse(url) ?: return@resourceFlow
                    val filename = when (uri.scheme) {
                        ContentResolver.SCHEME_FILE -> uri.toFile().name
                        ContentResolver.SCHEME_CONTENT -> {
                            context.contentResolver.query(
                                uri,
                                null,
                                null,
                                null,
                                null
                            )?.use { cursor ->
                                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (index == -1) null
                                else cursor.getString(index)
                            } ?: "File_${System.currentTimeMillis()}.txt"
                        }

                        else -> ""
                    }
                    withContext(Dispatchers.IO) {
                        val content = when (uri.scheme) {
                            ContentResolver.SCHEME_FILE -> uri.toFile().readText()
                            ContentResolver.SCHEME_CONTENT ->
                                context.contentResolver.openInputStream(uri)?.use {
                                    it.bufferedReader().readText()
                                }.orEmpty()

                            else -> ""
                        }
                        val file = File(context.filesDir, filename)
                        if (!file.exists()) {
                            file.createNewFile()
                        }
                        file.writeText(content)
                    }
                    diskParse(url)
                }

                else -> emptyList()
            }.map { live ->
                val uri = Uri.parse(live.url)
                when (uri.scheme) {
                    null -> live.copy(
                        url = parent + live.url
                    )
                    else -> live
                }
            }
            val feed = Feed(title, url)
            feedDao.insert(feed)
            val cachedLives = liveDao.getByFeedUrl(url)
            val skippedUrls = mutableListOf<String>()
            val groupedLives by lazy {
                cachedLives.groupBy { it.favourite }.withDefault { emptyList() }
            }
            val invalidateLives = when (strategy) {
                FeedStrategy.ALL -> cachedLives
                FeedStrategy.SKIP_FAVORITE -> groupedLives.getValue(false)
                else -> emptyList()
            }
            invalidateLives.forEach { live ->
                if (live belong lives) {
                    skippedUrls += live.url
                } else {
                    liveDao.deleteByUrl(live.url)
                }
            }
            val existedUrls = when (strategy) {
                FeedStrategy.ALL -> skippedUrls
                FeedStrategy.SKIP_FAVORITE -> groupedLives.getValue(true)
                    .map { it.url } + skippedUrls

                else -> emptyList()
            }
            lives
                .filterNot { it.url in existedUrls }
                .forEach {
                    liveDao.insert(it)
                }
            emitResource(Unit)
        } catch (e: Exception) {
            logger.log(e)
            emitMessage(e.message)
        }
    }

    private fun String.findParentPath(): String? {
        val index = lastIndexOf("/")
        if (index == -1) return null
        return take(index + 1)
    }

    private suspend fun networkParse(url: String): List<Live> = parse(url)

    private suspend fun diskParse(url: String): List<Live> {
        val uri = Uri.parse(url)
        val content = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        }.orEmpty()
        return parser.execute(content).map { it.toLive(url) }
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

    private suspend fun parse(url: String): List<Live> = parser.execute(
        url = url,
        connectTimeout = connectTimeout
    )
        .map { it.toLive(url) }

    override suspend fun rename(url: String, target: String) = logger.sandBox {
        feedDao.rename(url, target)
    }
}