package com.m3u.data.parser.xtream

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.data.database.model.DataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import javax.inject.Inject

internal class XtreamParserImpl @Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    okHttpClient: OkHttpClient,
    private val logger: Logger,
    @ApplicationContext context: Context
) : XtreamParser {
    @OptIn(ExperimentalSerializationApi::class)
    private val json: Json
        get() = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
        }
    private val okHttpClient = okHttpClient
        .newBuilder()
        .addInterceptor(
            ChuckerInterceptor.Builder(context)
                .maxContentLength(10240)
                .build()
        )
        .callTimeout(Duration.ofMillis(Int.MAX_VALUE.toLong()))
        .connectTimeout(Duration.ofMillis(Int.MAX_VALUE.toLong()))
        .readTimeout(Duration.ofMillis(Int.MAX_VALUE.toLong()))
        .build()

    override fun entityOutputs(input: XtreamInput): Flow<XtreamMediaOutput> = channelFlow {
        val (basicUrl, username, password, type) = input
        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES
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
        if (requiredLives) launch {
            newSequenceCall<XtreamLive>(liveStreamsUrl)
                .asFlow()
                .collect { live -> send(live) }
        }
        if (requiredVods) launch {
            newSequenceCall<XtreamVod>(vodStreamsUrl)
                .asFlow()
                .collect { vod -> send(vod) }
        }
        if (requiredSeries) launch {
            newSequenceCall<XtreamSerial>(seriesStreamsUrl)
                .asFlow()
                .collect { serial -> send(serial) }
        }
    }

    override suspend fun output(input: XtreamInput): XtreamOutput {
        val (basicUrl, username, password, type) = input
        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES
        val infoUrl = XtreamParser.createInfoUrl(basicUrl, username, password)
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

        val liveCategories: List<XtreamCategory> =
            if (requiredLives) newCall(liveCategoriesUrl) ?: emptyList() else emptyList()
        val vodCategories: List<XtreamCategory> =
            if (requiredVods) newCall(vodCategoriesUrl) ?: emptyList() else emptyList()
        val serialCategories: List<XtreamCategory> =
            if (requiredSeries) newCall(serialCategoriesUrl) ?: emptyList() else emptyList()

        return XtreamOutput(
            liveCategories = liveCategories,
            vodCategories = vodCategories,
            serialCategories = serialCategories,
            allowedOutputFormats = allowedOutputFormats,
            serverProtocol = serverProtocol,
            port = if (serverProtocol == "http") port else httpsPort
        )
    }

    override suspend fun getSeriesInfoOrThrow(input: XtreamInput, seriesId: Int): XtreamStreamInfo {
        val (basicUrl, username, password, type) = input
        check(type == DataSource.Xtream.TYPE_SERIES) { "xtream input type must be `series`" }
        return newCallOrThrow(
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

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> newCallOrThrow(url: String): T =
        withContext(ioDispatcher) {
            okHttpClient.newCall(
                Request.Builder().url(url).build()
            )
                .execute()
                .takeIf { it.isSuccessful }!!
                .body!!
                .byteStream()
                .let { json.decodeFromStream(it) }
        }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> newSequenceCall(url: String): Sequence<T> =
        logger.execute {
            okHttpClient.newCall(
                Request.Builder().url(url).build()
            )
                .execute()
                .takeIf { it.isSuccessful }
                ?.body
                ?.byteStream()
                ?.let { json.decodeToSequence(it) }
        } ?: sequence { }
}