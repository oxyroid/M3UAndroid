package com.m3u.data.repository.impl

import com.m3u.core.architecture.AbstractLogger
import com.m3u.core.architecture.Logger
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.local.entity.Release
import com.m3u.data.remote.api.GithubApi
import com.m3u.data.repository.RemoteRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class RemoteRepositoryImpl @Inject constructor(
    private val api: GithubApi,
    logger: Logger
) : RemoteRepository, AbstractLogger(logger) {
    override fun fetchLatestRelease(): Flow<Resource<Release>> = resourceFlow {
        try {
            val releases =
                api.getReleases(RemoteRepository.REPOS_AUTHOR, RemoteRepository.REPOS_NAME)
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