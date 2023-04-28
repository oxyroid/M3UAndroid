package com.m3u.data.repository

import com.m3u.data.database.entity.Post
import kotlinx.coroutines.flow.Flow

interface PostRepository {
    fun observeActivePosts(): Flow<List<Post>>
    suspend fun fetchAll()
    suspend fun read(id: Int)
    suspend fun temporal(post: Post)
    suspend fun clear()

    companion object {
        const val REPOS_AUTHOR = "thxbrop"
        const val REPOS_NAME_POST_PROJECT = "M3UAndroidPost"
    }
}