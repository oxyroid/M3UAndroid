package com.m3u.data.parser.internal

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.data.api.XtreamApi
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.XtreamCategory
import com.m3u.data.parser.XtreamInfo
import com.m3u.data.parser.XtreamInput
import com.m3u.data.parser.XtreamLive
import com.m3u.data.parser.XtreamOutput
import com.m3u.data.parser.XtreamParser
import com.m3u.data.parser.XtreamSerial
import com.m3u.data.parser.XtreamVod
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
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun execute(input: XtreamInput): XtreamOutput = withContext(ioDispatcher) {
        val (address, username, password, type) = input
        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES
        val infoUrl = XtreamApi.createInfoUrl(address, username, password)
        val liveStreamsUrl = XtreamApi.createActionUrl(
            address,
            username,
            password,
            XtreamApi.Action.GET_LIVE_STREAMS
        )
        val vodStreamsUrl = XtreamApi.createActionUrl(
            address,
            username,
            password,
            XtreamApi.Action.GET_VOD_STREAMS
        )
        val seriesStreamsUrl = XtreamApi.createActionUrl(
            address,
            username,
            password,
            XtreamApi.Action.GET_SERIES_STREAMS
        )
        val liveCategoriesUrl = XtreamApi.createActionUrl(
            address,
            username,
            password,
            XtreamApi.Action.GET_LIVE_CATEGORIES
        )
        val vodCategoriesUrl = XtreamApi.createActionUrl(
            address,
            username,
            password,
            XtreamApi.Action.GET_VOD_CATEGORIES
        )
        val serialCategoriesUrl = XtreamApi.createActionUrl(
            address,
            username,
            password,
            XtreamApi.Action.GET_SERIES_CATEGORIES
        )
        val info: XtreamInfo = newCall(infoUrl) ?: return@withContext XtreamOutput()
        val allowedOutputFormats = info.userInfo.allowedOutputFormats

        val lives: List<XtreamLive> = if (requiredLives) newCall(liveStreamsUrl) ?: emptyList() else emptyList()
        val vods: List<XtreamVod> = if (requiredVods) newCall(vodStreamsUrl) ?: emptyList() else emptyList()
        val series: List<XtreamSerial> = if (requiredSeries) newCall(seriesStreamsUrl) ?: emptyList() else emptyList()

        val liveCategories: List<XtreamCategory> = if (requiredLives) newCall(liveCategoriesUrl) ?: emptyList() else emptyList()
        val vodCategories: List<XtreamCategory> = if (requiredVods) newCall(vodCategoriesUrl) ?: emptyList() else emptyList()
        val serialCategories: List<XtreamCategory> = if (requiredSeries) newCall(serialCategoriesUrl) ?: emptyList() else emptyList()

        XtreamOutput(
            lives = lives,
            vods = vods,
            series = series,
            liveCategories = liveCategories,
            vodCategories = vodCategories,
            serialCategories = serialCategories,
            allowedOutputFormats = allowedOutputFormats
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> newCall(url: String): T? = logger.execute {
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