package com.m3u.data.parser.m3u

import dev.oxyroid.parser.m3u.M3UPlaylistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import javax.inject.Inject

internal class M3UParserImpl @Inject constructor() : M3UParser {
    override fun parse(input: InputStream) = M3UPlaylistParser
        .parse(input)
        .asFlow()
        .flowOn(Dispatchers.Default)
}
