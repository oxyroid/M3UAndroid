package com.m3u.data.repository

import com.m3u.data.database.entity.Stream

interface StreamRepository : ReadOnlyRepository<Stream, Int> {
    suspend fun getByUrl(url: String): Stream?
    suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream>
    suspend fun setFavourite(id: Int, target: Boolean)
    suspend fun setBanned(id: Int, target: Boolean)
}
