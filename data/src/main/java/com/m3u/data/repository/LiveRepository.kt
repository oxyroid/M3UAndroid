package com.m3u.data.repository

import com.m3u.data.local.entity.Live
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
    suspend fun setBanned(id: Int, target: Boolean)
}

fun LiveRepository.observeByFeedUrl(feedUrl: String): Flow<List<Live>> = observeAll()
    .map { lives ->
        lives.filter { it.feedUrl == feedUrl }
    }
    .distinctUntilChanged()

fun LiveRepository.observeBanned(banned: Boolean): Flow<List<Live>> = observeAll()
    .map { lives ->
        lives.filter { it.banned == banned }
    }
    .distinctUntilChanged()

fun LiveRepository.observeBannedByFeedUrl(
    feedUrl: String,
    banned: Boolean
): Flow<List<Live>> = observeAll()
    .map { lives ->
        lives.filter { it.feedUrl == feedUrl && it.banned == banned }
    }
    .distinctUntilChanged()
