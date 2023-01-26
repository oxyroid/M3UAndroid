package com.m3u.data.repository

import com.m3u.data.dao.LiveDao
import com.m3u.data.entity.Live
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface LiveRepository {
    fun observe(id: Int): Flow<Live?>
    fun observeBySubscriptionId(id: Int): Flow<List<Live>>
    suspend fun getBySubscriptionId(id: Int): List<Live>
}

class LiveRepositoryImpl @Inject constructor(
    private val liveDao: LiveDao
) : LiveRepository {

    override fun observe(id: Int): Flow<Live?> = liveDao.observeById(id)

    override fun observeBySubscriptionId(id: Int): Flow<List<Live>> =
        liveDao.observeBySubscriptionId(id)

    override suspend fun getBySubscriptionId(id: Int): List<Live> = liveDao.getBySubscriptionId(id)
}