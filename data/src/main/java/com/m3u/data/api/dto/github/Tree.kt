package com.m3u.data.api.dto.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tree(
    val sha: String,
    @SerialName("tree")
    val leaves: List<Leaf>,
    val truncated: Boolean,
    val url: String
)
