package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import com.m3u.data.entity.Release
import kotlinx.coroutines.flow.Flow

interface RemoteRepository {
    fun fetchLatestRelease(): Flow<Resource<Release>>

    companion object {
        const val REPOS_AUTHOR = "thxbrop"
        const val REPOS_NAME = "M3UAndroid"
    }
}