package com.m3u.data.repository

import android.util.Log
import com.m3u.data.dao.LiveDao
import com.m3u.data.entity.Live
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

private const val TAG = "LiveRepository"
interface LiveRepository {
    fun observe(id: Int): Flow<Live?>
    fun observeAllLives(): Flow<List<Live>>
    fun observeLivesBySubscriptionUrl(subscriptionUrl: String): Flow<List<Live>>
    suspend fun getBySubscriptionUrl(subscriptionUrl: String): List<Live>

    suspend fun getByUrl(url: String): Live?
    suspend fun setFavouriteLive(id: Int, target: Boolean)
}

class LiveRepositoryImpl @Inject constructor(
    private val liveDao: LiveDao
) : LiveRepository {
    override fun observe(id: Int): Flow<Live?> = try {
        liveDao.observeById(id)
    } catch (e: Exception) {
        Log.e(TAG, "observe: ", e)
        flow {}
    }

    override fun observeAllLives(): Flow<List<Live>> = try {
        liveDao.observeAll()
    } catch (e: Exception) {
        Log.e(TAG, "observeAllLives: ", e)
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

    override suspend fun getByUrl(url: String): Live? = try {
        liveDao.getByUrl(url)
    } catch (e: Exception) {
        Log.e(TAG, "getByUrl: ", e)
        null
    }

    override suspend fun setFavouriteLive(id: Int, target: Boolean) = try {
        liveDao.setFavouriteLive(id, target)
    } catch (e: Exception) {
        Log.e(TAG, "setFavouriteLive: ", e)
        Unit
    }
}