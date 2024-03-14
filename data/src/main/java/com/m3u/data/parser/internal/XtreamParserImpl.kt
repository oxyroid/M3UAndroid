package com.m3u.data.parser.internal

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.data.api.xtream.XtreamCategory
import com.m3u.data.api.xtream.XtreamInfo
import com.m3u.data.api.xtream.XtreamLive
import com.m3u.data.api.xtream.XtreamSerial
import com.m3u.data.api.xtream.XtreamStreamInfo
import com.m3u.data.api.xtream.XtreamVod
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.XtreamInput
import com.m3u.data.parser.XtreamOutput
import com.m3u.data.parser.XtreamParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

internal class XtreamParserImpl @Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val okHttpClient: OkHttpClient,
    private val logger: Logger
) : XtreamParser {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    override suspend fun execute(
        input: XtreamInput,
        callback: (count: Int, total: Int) -> Unit
    ): XtreamOutput {
        var currentCount = 0
        callback(currentCount, -1)
        val (basicUrl, username, password, type) = input
        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES
        val infoUrl = XtreamParser.createInfoUrl(basicUrl, username, password)
        val liveStreamsUrl = XtreamParser.createActionUrl(
            basicUrl,
            username,
            password,
            XtreamParser.Action.GET_LIVE_STREAMS
        )
        val vodStreamsUrl = XtreamParser.createActionUrl(
            basicUrl,
            username,
            password,
            XtreamParser.Action.GET_VOD_STREAMS
        )
        val seriesStreamsUrl = XtreamParser.createActionUrl(
            basicUrl,
            username,
            password,
            XtreamParser.Action.GET_SERIES_STREAMS
        )
        val liveCategoriesUrl = XtreamParser.createActionUrl(
            basicUrl,
            username,
            password,
            XtreamParser.Action.GET_LIVE_CATEGORIES
        )
        val vodCategoriesUrl = XtreamParser.createActionUrl(
            basicUrl,
            username,
            password,
            XtreamParser.Action.GET_VOD_CATEGORIES
        )
        val serialCategoriesUrl = XtreamParser.createActionUrl(
            basicUrl,
            username,
            password,
            XtreamParser.Action.GET_SERIES_CATEGORIES
        )
        val info: XtreamInfo = newCall(infoUrl) ?: return XtreamOutput()
        val allowedOutputFormats = info.userInfo.allowedOutputFormats
        val serverProtocol = info.serverInfo.serverProtocol ?: "http"
        val port = info.serverInfo.port?.toIntOrNull()
        val httpsPort = info.serverInfo.httpsPort?.toIntOrNull()

        val lives: List<XtreamLive> =
            if (requiredLives) newCall(liveStreamsUrl) ?: emptyList() else emptyList()
        currentCount += lives.size
        callback(currentCount, -1)
        val vods: List<XtreamVod> =
            if (requiredVods) newCall(vodStreamsUrl) ?: emptyList() else emptyList()
        currentCount += vods.size
        callback(currentCount, -1)
        val series: List<XtreamSerial> =
            if (requiredSeries) newCall(seriesStreamsUrl) ?: emptyList() else emptyList()
        currentCount += series.size
        callback(currentCount, -1)

        val liveCategories: List<XtreamCategory> =
            if (requiredLives) newCall(liveCategoriesUrl) ?: emptyList() else emptyList()
        val vodCategories: List<XtreamCategory> =
            if (requiredVods) newCall(vodCategoriesUrl) ?: emptyList() else emptyList()
        val serialCategories: List<XtreamCategory> =
            if (requiredSeries) newCall(serialCategoriesUrl) ?: emptyList() else emptyList()

        return XtreamOutput(
            lives = lives,
            vods = vods,
            series = series,
            liveCategories = liveCategories,
            vodCategories = vodCategories,
            serialCategories = serialCategories,
            allowedOutputFormats = allowedOutputFormats,
            serverProtocol = serverProtocol,
            port = if (serverProtocol == "http") port else httpsPort
        )
    }

    override suspend fun getSeriesInfo(input: XtreamInput, seriesId: Int): XtreamStreamInfo? {
        val (basicUrl, username, password, type) = input
        check(type == DataSource.Xtream.TYPE_SERIES) { "xtream input type must be `series`" }
        return newCall(
            XtreamParser.createActionUrl(
                basicUrl,
                username,
                password,
                XtreamParser.Action.GET_SERIES_INFO,
                XtreamParser.GET_SERIES_INFO_PARAM_ID to seriesId
            )
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> newCall(url: String): T? = withContext(ioDispatcher) {
        logger.execute {
            okHttpClient.newCall(
                Request.Builder().url(url).build()
            )
                .execute()
                .takeIf { it.isSuccessful }
                ?.body
                ?.byteStream()
                ?.let { json.decodeFromStream(it) }
        }
    }
}