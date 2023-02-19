package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import com.m3u.data.entity.GitRelease
import kotlinx.coroutines.flow.Flow

interface RemoteRepository {
    fun fetchLatestRelease(): Flow<Resource<GitRelease>>

    companion object {
        const val REPOS_AUTHOR = "thxbrop"
        const val REPOS_NAME = "M3UAndroid"
    }
}