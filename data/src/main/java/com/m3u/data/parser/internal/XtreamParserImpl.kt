package com.m3u.data.parser.internal

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.data.api.XtreamApi
import com.m3u.data.parser.XtreamData
import com.m3u.data.parser.XtreamInfo
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
    @Dispatcher(M3uDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    private val okHttpClient: OkHttpClient,
    private val logger: Logger
) : XtreamParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun execute(input: XtreamInput): XtreamOutput = withContext(ioDispatcher) {
        val (address, username, password) = input
        val infoUrl = XtreamApi.createInfoUrl(address, username, password)
        val actionUrl = XtreamApi.createActionUrl(
            address,
            username,
            password,
            XtreamApi.GetAll.GET_LIVE_STREAMS
        )
        val info: XtreamInfo? = logger.execute {
            okHttpClient.newCall(
                Request.Builder()
                    .url(infoUrl)
                    .build()
            )
                .execute()
                .takeIf { it.isSuccessful }
                ?.body
                ?.byteStream()
                ?.let { json.decodeFromStream(it) }
        }

        info ?: return@withContext XtreamOutput()

        val allowedOutputFormats = info.userInfo.allowedOutputFormats

        val all: List<XtreamData> = logger.execute {
            okHttpClient.newCall(
                Request.Builder()
                    .url(actionUrl)
                    .build()
            )
                .execute()
                .takeIf { it.isSuccessful }
                ?.body
                ?.byteStream()
                ?.let { json.decodeFromStream(it) }
        } ?: emptyList()

        XtreamOutput(
            all = all,
            allowedOutputFormats = allowedOutputFormats
        )
    }
}