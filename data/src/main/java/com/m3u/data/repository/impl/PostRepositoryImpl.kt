package com.m3u.data.repository.impl

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.sandBox
import com.m3u.data.database.dao.PostDao
import com.m3u.data.database.entity.Post
import com.m3u.data.database.entity.PostDTO
import com.m3u.data.database.entity.toPost
import com.m3u.data.remote.api.RemoteApi
import com.m3u.data.repository.PostRepository
import java.net.URL
import java.util.Locale
import kotlin.streams.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class PostRepositoryImpl constructor(
    private val dao: PostDao,
    private val logger: Logger,
    private val api: RemoteApi,
    private val json: Json
) : PostRepository {
    override fun observeActivePosts(): Flow<List<Post>> = dao.observeActivePosts()

    override suspend fun fetchAll() = logger.sandBox {
        val author = PostRepository.REPOS_AUTHOR
        val repos = PostRepository.REPOS_NAME_POST_PROJECT
        val language = getSystemLanguage()
        val posts = api
            .postContents(
                author = author,
                repos = repos,
                language = language
            )
            .mapNotNull { content ->
                val downloadUrl = content.downloadUrl
                withContext(Dispatchers.IO) {
                    val parts = content.name.split(".")
                    val id = if (parts.size > 1) parts.first().toInt()
                    else parts.dropLast(1).joinToString("").toInt()
                    val url = URL(downloadUrl)
                    try {
                        val jsonString = url.openConnection()
                            .getInputStream()
                            .use { stream ->
                                stream
                                    .bufferedReader()
                                    .lines()
                                    .toList()
                                    .joinToString("")
                            }
                        json.decodeFromString<PostDTO>(jsonString).toPost(id)
                    } catch (e: Exception) {
                        logger.log(e)
                        null
                    }
                }
            }
        posts.forEach {
            dao.insert(it)
        }
    }

    override suspend fun read(id: Int) = logger.sandBox {
        dao.read(id)
    }

    private var temporalJob: Job? = null
    override suspend fun temporal(post: Post) = coroutineScope {
        temporalJob?.cancel()
        temporalJob = launch {
            logger.sandBox {
                dao.insert(post)
                delay(2500L)
                dao.delete(post)
            }
        }
    }

    override suspend fun clear() = logger.sandBox {
        dao.clear()
    }

    private fun getSystemLanguage(): String {
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        return if (language.equals("zh", true)) {
            if (country.equals("cn", true)) "zh-cn"
            else "zh-tw"
        } else "en-ww"
    }
}