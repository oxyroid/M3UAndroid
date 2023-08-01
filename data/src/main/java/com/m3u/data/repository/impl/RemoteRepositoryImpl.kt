package com.m3u.data.repository.impl

import com.m3u.core.architecture.logger.FileLoggerImpl
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitException
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.remote.api.RemoteApi
import com.m3u.data.remote.api.dto.Release
import com.m3u.data.repository.RemoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RemoteRepositoryImpl @Inject constructor(
    private val api: RemoteApi,
    @FileLoggerImpl private val logger: Logger,
) : RemoteRepository {
    override fun fetchLatestRelease(): Flow<Resource<Release>> = resourceFlow {
        try {
            val releases =
                api.releases(RemoteRepository.REPOS_AUTHOR, RemoteRepository.REPOS_NAME_PROJECT)
            val release = releases.firstOrNull()
            if (release == null) {
                error("Cannot fetch latest release.")
            } else {
                emitResource(release)
            }
        } catch (e: Exception) {
            logger.log(e)
            emitException(e)
        }
    }
}