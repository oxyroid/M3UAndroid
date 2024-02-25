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
            action: Action
        ): String = createInfoUrl(address, username, password) + "&action=$action"
    }

    @JvmInline
    @Serializable
    value class Action(val path: String) {
        override fun toString(): String = path

        companion object {
            val GET_LIVE_STREAMS = Action("get_live_streams")
            val GET_VOD_STREAMS = Action("get_vod_streams")
            val GET_SERIES_STREAMS = Action("get_series")
            val GET_LIVE_CATEGORIES = Action("get_live_categories")
            val GET_VOD_CATEGORIES = Action("get_vod_categories")
            val GET_SERIES_CATEGORIES = Action("get_series_categories")
            fun of(value: String): Action {
                return when (value) {
                    GET_LIVE_STREAMS.path -> GET_LIVE_STREAMS
                    GET_VOD_STREAMS.path -> GET_VOD_STREAMS
                    GET_SERIES_STREAMS.path -> GET_SERIES_STREAMS
                    GET_LIVE_CATEGORIES.path -> GET_LIVE_CATEGORIES
                    GET_VOD_CATEGORIES.path -> GET_VOD_CATEGORIES
                    GET_SERIES_CATEGORIES.path -> GET_SERIES_CATEGORIES
                    else -> throw IllegalArgumentException(value)
                }
            }
        }
    }
}
