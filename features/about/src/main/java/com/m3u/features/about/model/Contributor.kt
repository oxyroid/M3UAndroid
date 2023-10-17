package com.m3u.features.about.model

import androidx.compose.runtime.Immutable
import com.m3u.data.api.dto.github.User

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
