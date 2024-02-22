package com.m3u.data.api

import com.m3u.data.parser.XtreamData
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApi {
    companion object {
        fun createBaseUrl(
            scheme: String,
            host: String,
            port: String,
            username: String,
            password: String
        ): String = "$scheme://$host:$port/api.php?username=$username&password=$password"
    }

    @GET("/")
    suspend fun getAll(@Query("action") action: GetAll): List<XtreamData>

    @GET("/")
    suspend fun getInfo(@Query("action") action: GetInfo): XtreamData?

    @JvmInline
    value class GetAll(val path: String) {
        companion object {
            val GET_LIVE_STREAMS = GetAll("get_live_streams")
            val GET_VOD_STREAMS = GetAll("get_vod_streams")
            val GET_SERIES_STREAMS = GetAll("get_series")
        }
    }

    @JvmInline
    value class GetInfo(val path: String) {
        companion object {
            val GET_LIVE_STREAMS = GetInfo("get_live_streams")
            val GET_VOD_STREAMS = GetInfo("get_vod_info")
            val GET_SERIES = GetInfo("get_series_info")
        }
    }
}
