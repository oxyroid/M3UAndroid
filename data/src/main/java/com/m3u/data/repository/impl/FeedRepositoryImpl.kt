package com.m3u.data.repository.impl

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.core.net.toFile
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.logger.BannerLoggerImpl
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.util.collection.belong
import com.m3u.core.wrapper.ProgressResource
import com.m3u.core.wrapper.emitException
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitProgress
import com.m3u.core.wrapper.emitResource
import com.m3u.data.R
import com.m3u.data.database.dao.FeedDao
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import com.m3u.data.remote.parser.execute
import com.m3u.data.remote.parser.m3u.InvalidatePlaylistError
import com.m3u.data.remote.parser.m3u.PlaylistParser
import com.m3u.data.remote.parser.m3u.toLive
import com.m3u.data.repository.FeedRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val liveDao: LiveDao,
    @BannerLoggerImpl private val logger: Logger,
    configuration: Configuration,
    private val parser: PlaylistParser,
    @ApplicationContext private val context: Context
) : FeedRepository {
    private val connectTimeout by configuration.connectTimeout

    override fun subscribe(
        title: String,
        url: String,
        strategy: Int
    ): Flow<ProgressResource<Unit>> = flow {
        try {
            val lives = when {
                url.startsWith("http://") || url.startsWith("https://") -> networkParse(url)
                url.startsWith("file://") || url.startsWith("content://") -> {
                    val uri = Uri.parse(url) ?: run {
                        emitMessage("Url is empty")
                        return@flow
                    }
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
            var progress = 0
            lives
                .filterNot { it.url in existedUrls }
                .forEach {
                    liveDao.insert(it)
                    progress++
                    emitProgress(progress)
                }
            emitResource(Unit)
        } catch (e: InvalidatePlaylistError) {
            val feed = Feed("", Feed.URL_IMPORTED)
            val live = Live(
                url = url,
                group = "",
                title = title,
                feedUrl = feed.url
            )
            if (feedDao.getByUrl(feed.url) == null) {
                feedDao.insert(feed)
            }
            liveDao.insert(live)
            emitResource(Unit)
        } catch (e: FileNotFoundException) {
            error(context.getString(R.string.error_file_not_found))
        } catch (e: Exception) {
            logger.log(e)
            emitException(e)
        }
    }

    @Throws(InvalidatePlaylistError::class)
    private suspend fun networkParse(url: String): List<Live> = parse(url)

    @Throws(InvalidatePlaylistError::class)
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