package com.m3u.data.net.broadcast

import kotlinx.coroutines.flow.Flow

interface Broadcast {
    fun send(bytes: ByteArray)
    fun receive(): Flow<ByteArray>
}
