package com.m3u.features.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.data.database.entity.Post
import com.m3u.features.main.model.FeedDetail

data class MainState(
    val loading: Boolean = false,
    private val configuration: Configuration,
    val feeds: List<FeedDetail> = emptyList(),
    val posts: List<Post> = emptyList(),
    val post: Post? = null
) {
    var godMode: Boolean by configuration.godMode
    var rowCount: Int by configuration.rowCount
}