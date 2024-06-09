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
    private var releases: List<Release>? = null
    override suspend fun release(): Release? {
        if (releases == null) {
            // cannot use lazy because the suspension
            releases = logger.execute { githubApi.releases("oxyroid", "M3UAndroid") } ?: emptyList()
        }
        val versionName = publisher.versionName
        val currentReleases = releases ?: emptyList()
        if (currentReleases.isEmpty()) return null
        val i = currentReleases.indexOf { it.name == versionName }
        if (i <= 0 && !publisher.snapshot) return null
        return currentReleases.first()
    }
}