package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import com.m3u.data.entity.Live
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface LiveRepository {
    fun observe(id: Int): Flow<Live?>
    fun observeAll(): Flow<List<Live>>
    suspend fun get(id: Int): Live?
    suspend fun getByUrl(url: String): Live?
    suspend fun getByFeedUrl(feedUrl: String): List<Live>
    suspend fun setFavourite(id: Int, target: Boolean)
    fun setMuteByUrl(url: String, target: Boolean): Flow<Resource<Unit>>
}

fun LiveRepository.observeByFeedUrl(feedUrl: String): Flow<List<Live>> = observeAll()
    .map { lives ->
        lives.filter { it.feedUrl == feedUrl }
    }
    .distinctUntilChanged()