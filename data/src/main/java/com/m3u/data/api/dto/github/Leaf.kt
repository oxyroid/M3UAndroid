package com.m3u.data.api.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class Leaf(
    val mode: String,
    val path: String,
    val sha: String,
    val size: Int,
    val type: String,
    val url: String
)
