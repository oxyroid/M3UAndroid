package com.m3u.data.service

import com.m3u.data.television.model.RemoteDirection
import kotlinx.coroutines.flow.SharedFlow

interface RemoteDirectionService {
    val remoteDirection: SharedFlow<RemoteDirection>
    fun handle(remoteDirection: RemoteDirection)
}