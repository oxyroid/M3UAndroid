package com.m3u.data.remote.parser.udp

import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.Flow

interface UdpDiscover {
    fun start(): Flow<String>
    fun scan(): Flow<List<NsdServiceInfo>>
}