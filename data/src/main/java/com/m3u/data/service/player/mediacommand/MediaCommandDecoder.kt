package com.m3u.data.service.player.mediacommand

import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import kotlinx.coroutines.flow.Flow

internal interface MediaCommandDecoder {
    fun decodePlaylist(mediaCommand: Flow<MediaCommand?>): Flow<Playlist?>
    fun decodeChannel(mediaCommand: Flow<MediaCommand?>): Flow<Channel?>
    suspend fun decodePlaylist(mediaCommand: MediaCommand?): Playlist?
    suspend fun decodeChannel(mediaCommand: MediaCommand?): Channel?
}