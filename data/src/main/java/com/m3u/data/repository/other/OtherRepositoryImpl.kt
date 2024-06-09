package com.m3u.data.repository.other

import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.install
import com.m3u.core.util.collections.indexOf
import com.m3u.data.api.GithubApi
import com.m3u.data.api.dto.github.Release
import javax.inject.Inject

internal class OtherRepositoryImpl @Inject constructor(
    private val githubApi: GithubApi,
    private val publisher: Publisher,
    delegate: Logger
) : OtherRepository {
    private val logger = delegate.install(Profiles.REPOS_OTHER)
    override suspend fun getRelease(): Release? {
        if (publisher.snapshot) return null
        val versionName = publisher.versionName
        val releases = logger
            .execute { githubApi.releases("oxyroid", "M3UAndroid") }
            ?: emptyList()
        if (releases.isEmpty()) return null
        val i = releases.indexOf { it.name == versionName }
//        if (i <= 0) return null
        return releases.first()
    }
}