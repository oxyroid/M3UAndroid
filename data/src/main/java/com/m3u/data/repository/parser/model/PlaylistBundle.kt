package com.m3u.data.repository.parser.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistBundle(
    val info: Info,
    val playlists: List<Playlist> = emptyList()
) {

    @Serializable
    data class Info(
        val name: String,
        val description: String? = null
    )

    @Serializable
    data class Playlist(
        val name: String,
        val url: String
    )
}
