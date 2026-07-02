package com.m3u.data.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class RestorePlaylistPayload(
    val backup: String
)
