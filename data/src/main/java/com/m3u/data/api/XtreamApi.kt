package com.m3u.data.api

import kotlinx.serialization.Serializable

interface XtreamApi {
    companion object {
        fun createInfoUrl(
            address: String,
            username: String,
            password: String
        ): String = "$address/player_api.php?username=$username&password=$password"

        fun createActionUrl(
            address: String,
            username: String,
            password: String,
            action: GetAll
        ): String = createInfoUrl(address, username, password) + "&action=$action"
    }

    @JvmInline
    @Serializable
    value class GetAll(val path: String) {
        override fun toString(): String = path
        companion object {
            val GET_LIVE_STREAMS = GetAll("get_live_streams")
            val GET_VOD_STREAMS = GetAll("get_vod_streams")
            val GET_SERIES_STREAMS = GetAll("get_series")
        }
    }
}
