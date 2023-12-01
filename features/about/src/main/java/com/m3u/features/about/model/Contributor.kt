package com.m3u.features.about.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.m3u.data.api.dto.github.User

@Composable
internal fun rememberContributorHolder(contributors: List<Contributor>): ContributorHolder {
    return remember(contributors) {
        ContributorHolder(contributors)
    }
}

@Immutable
internal data class ContributorHolder(
    val contributions: List<Contributor>
)

@Immutable
internal data class Contributor(
    val avatar: String,
    val contributions: Int,
    val name: String,
    val url: String
)

internal fun User.toContributor(): Contributor = Contributor(
    avatar = avatarUrl,
    contributions = contributions,
    name = login,
    url = htmlUrl
)
