package com.m3u.data.repository.watchprogress

import com.m3u.data.database.dao.WatchProgressDao
import com.m3u.data.database.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WatchProgressRepositoryImpl @Inject constructor(
    private val watchProgressDao: WatchProgressDao
) : WatchProgressRepository {

    override suspend fun upsert(watchProgress: WatchProgress) {
        watchProgressDao.upsert(watchProgress)
    }

    override suspend fun getByChannelId(channelId: Int): WatchProgress? {
        return watchProgressDao.getByChannelId(channelId)
    }

    override fun observeByChannelId(channelId: Int): Flow<WatchProgress?> {
        return watchProgressDao.observeByChannelId(channelId)
    }

    override fun getContinueWatching(limit: Int): Flow<List<WatchProgress>> {
        return watchProgressDao.getContinueWatching(limit)
    }

    override suspend fun deleteByChannelId(channelId: Int) {
        watchProgressDao.deleteByChannelId(channelId)
    }

    override suspend fun deleteAll() {
        watchProgressDao.deleteAll()
    }
}
