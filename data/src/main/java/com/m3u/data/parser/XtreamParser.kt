package com.m3u.data.parser

import com.m3u.core.util.basic.startsWithAny
import com.m3u.data.api.xtream.XtreamCategory
import com.m3u.data.api.xtream.XtreamLive
import com.m3u.data.api.xtream.XtreamSerial
import com.m3u.data.api.xtream.XtreamVod
import com.m3u.data.api.xtream.XtreamVodInfo
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

interface XtreamParser : Parser<XtreamInput, XtreamOutput> {
    suspend fun getVodInfo(
        input: XtreamInput,
        vodId: Int
    ): XtreamVodInfo?

    companion object {
        fun createInfoUrl(
            address: String,
            username: String,
            password: String,
            vararg params: Pair<String, Any>
        ): String {
            val url = Url(address)
            val builder = HttpUrl.Builder()
                .scheme("http")
                .host(url.host)
                .port(url.port)
                .addPathSegment("player_api.php")
                .addQueryParameter("username", username)
                .addQueryParameter("password", password)

            return params
                .fold(builder) { prev, (k, v) -> prev.addQueryParameter(k, v.toString()) }
                .build()
                .toString()
        }

        fun createActionUrl(
            address: String,
            username: String,
            password: String,
            action: Action,
            vararg params: Pair<String, Any>
        ): String = createInfoUrl(address, username, password, *params) + "&action=$action"

        val GET_VOD_INFO_PARAM_VOD_ID = "vod_id"
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
            val GET_VOD_INFO = Action("get_vod_info")
            fun of(value: String): Action {
                return when (value) {
                    GET_LIVE_STREAMS.path -> GET_LIVE_STREAMS
                    GET_VOD_STREAMS.path -> GET_VOD_STREAMS
                    GET_SERIES_STREAMS.path -> GET_SERIES_STREAMS
                    GET_LIVE_CATEGORIES.path -> GET_LIVE_CATEGORIES
                    GET_VOD_CATEGORIES.path -> GET_VOD_CATEGORIES
                    GET_SERIES_CATEGORIES.path -> GET_SERIES_CATEGORIES
                    GET_VOD_INFO.path -> GET_VOD_INFO
                    else -> throw IllegalArgumentException(value)
                }
            }
        }
    }
}

data class XtreamInput(
    val address: String, // scheme + host + port
    val username: String,
    val password: String,
    // DataSource.Xtream.TYPE_LIVE, DataSource.Xtream.TYPE_VOD, DataSource.Xtream.TYPE_SERIES
    val type: String? = null // null means all
) {
    companion object {
        fun decodeFromPlaylistUrl(url: String): XtreamInput {
            val hasScheme = url.startsWithAny("http:", "https:", ignoreCase = true)
            val httpUrl = if (hasScheme) url.toHttpUrl() else "http://$url".toHttpUrl()
            val username = httpUrl.queryParameter("username").orEmpty()
            val password = httpUrl.queryParameter("password").orEmpty()
            return XtreamInput(
                address = "${httpUrl.scheme}://${httpUrl.host}:${httpUrl.port}",
                username = username,
                password = password,
                type = httpUrl.queryParameter("type")
            )
        }

        fun decodeFromUrl(url: String): XtreamInput {
            val hasScheme = url.startsWithAny("http:", "https:", ignoreCase = true)
            val httpUrl = if (hasScheme) url.toHttpUrl() else "http://$url".toHttpUrl()
            val username = httpUrl.pathSegments.getOrNull(1).orEmpty()
            val password = httpUrl.pathSegments.getOrNull(2).orEmpty()
            return XtreamInput(
                address = "${httpUrl.scheme}://${httpUrl.host}:${httpUrl.port}",
                username = username,
                password = password,
                type = httpUrl.queryParameter("type")
            )
        }

        fun encodeToUrl(input: XtreamInput): String {
            return with(input) {
                buildString {
                    append("$address/player_api.php?username=$username&password=$password&")
                    if (type != null) {
                        append("type=$type")
                    }
                }
            }
        }

        fun decodeFromPlaylistUrlOrNull(url: String): XtreamInput? =
            runCatching { decodeFromPlaylistUrl(url) }.getOrNull()

        fun decodeFromUrlOrNull(url: String): XtreamInput? =
            runCatching { decodeFromUrl(url) }.getOrNull()
    }
}

data class XtreamOutput(
    val lives: List<XtreamLive> = emptyList(),
    val vods: List<XtreamVod> = emptyList(),
    val series: List<XtreamSerial> = emptyList(),
    val liveCategories: List<XtreamCategory> = emptyList(),
    val vodCategories: List<XtreamCategory> = emptyList(),
    val serialCategories: List<XtreamCategory> = emptyList(),
    val allowedOutputFormats: List<String> = emptyList()
)
