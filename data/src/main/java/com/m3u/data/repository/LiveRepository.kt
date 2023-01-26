package com.m3u.data.repository

import android.util.Log
import com.m3u.data.dao.LiveDao
import com.m3u.data.entity.Live
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

interface LiveRepository {
    fun observe(id: Int): Flow<Live?>
    fun observeLivesBySubscriptionUrl(subscriptionUrl: String): Flow<List<Live>>
    suspend fun getBySubscriptionUrl(subscriptionUrl: String): List<Live>
}

class LiveRepositoryImpl @Inject constructor(
    private val liveDao: LiveDao
) : LiveRepository {
    private val TAG = "LiveRepositoryImpl"
    override fun observe(id: Int): Flow<Live?> = try {
        liveDao.observeById(id)
    } catch (e: Exception) {
        Log.e(TAG, "observe: ", e)
        flow {}
    }

    override fun observeLivesBySubscriptionUrl(subscriptionUrl: String): Flow<List<Live>> = try {
        liveDao.observeLivesBySubscriptionUrl(subscriptionUrl)
    } catch (e: Exception) {
        Log.e(TAG, "observeBySubscriptionId: ", e)
        flow {}
    }

    override suspend fun getBySubscriptionUrl(subscriptionUrl: String): List<Live> = try {
        liveDao.getBySubscriptionId(subscriptionUrl)
    } catch (e: Exception) {
        Log.e(TAG, "getBySubscriptionId: ", e)
        emptyList()
    }
}