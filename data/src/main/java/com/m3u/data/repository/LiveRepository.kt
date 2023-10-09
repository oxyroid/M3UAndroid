package com.m3u.data.repository

import com.m3u.data.database.entity.Live

interface LiveRepository : Repository<Live, Int> {
    suspend fun getByUrl(url: String): Live?
    suspend fun getByFeedUrl(feedUrl: String): List<Live>
    suspend fun setFavourite(id: Int, target: Boolean)
    suspend fun setBanned(id: Int, target: Boolean)
}
