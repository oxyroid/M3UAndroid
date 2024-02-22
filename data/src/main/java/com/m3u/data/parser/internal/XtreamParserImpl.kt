package com.m3u.data.parser.internal

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers
import com.m3u.data.api.XtreamApi
import com.m3u.data.parser.XtreamData
import com.m3u.data.parser.XtreamParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

internal class XtreamParserImpl @Inject constructor(
    @Dispatcher(M3uDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    private val xtreamApi: XtreamApi
): XtreamParser {
    override suspend fun execute(input: InputStream): List<XtreamData> = withContext(ioDispatcher) {
        TODO()
    }
}