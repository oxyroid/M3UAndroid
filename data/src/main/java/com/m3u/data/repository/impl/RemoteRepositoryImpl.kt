package com.m3u.data.repository.impl

import com.m3u.core.architecture.logger.FileLoggerImpl
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.database.entity.Release
import com.m3u.data.remote.api.GithubRepositoryApi
import com.m3u.data.repository.RemoteRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class RemoteRepositoryImpl @Inject constructor(
    private val api: GithubRepositoryApi,
    @FileLoggerImpl private val logger: Logger
) : RemoteRepository {
    override fun fetchLatestRelease(): Flow<Resource<Release>> = resourceFlow {
        try {
            val releases = api.releases(RemoteRepository.REPOS_AUTHOR, RemoteRepository.REPOS_NAME)
            val release = releases.firstOrNull()
            if (release == null) {
                emitMessage("Cannot fetch latest release.")
            } else {
                emitResource(release)
            }
        } catch (e: Exception) {
            logger.log(e)
            emitMessage(e.message)
        }
    }
}