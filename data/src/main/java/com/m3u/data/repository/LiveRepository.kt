package com.m3u.data.repository

import com.m3u.data.entity.Live
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface LiveRepository {
    fun observe(id: Int): Flow<Live?>
    fun observeAllLives(): Flow<List<Live>>
    suspend fun getByFeedUrl(feedUrl: String): List<Live>
    suspend fun getByUrl(url: String): Live?
    suspend fun setFavouriteLive(id: Int, target: Boolean)
}

fun LiveRepository.observeLivesByFeedUrl(feedUrl: String): Flow<List<Live>> = observeAllLives()
    .map { lives ->
        lives.filter { it.feedUrl == feedUrl }
    }
    .distinctUntilChanged()