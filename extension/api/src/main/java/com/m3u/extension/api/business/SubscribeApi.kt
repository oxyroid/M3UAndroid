package com.m3u.extension.api.business

import com.m3u.extension.api.Method
import com.m3u.extension.api.Module
import com.m3u.extension.api.model.AddChannelRequest
import com.m3u.extension.api.model.AddPlaylistRequest
import com.m3u.extension.api.model.Result

@Module("subscribe")
interface SubscribeApi {
    @Method("addPlaylist")
    suspend fun addPlaylist(req: AddPlaylistRequest): Result

    @Method("addChannel")
    suspend fun addChannel(req: AddChannelRequest): Result
}