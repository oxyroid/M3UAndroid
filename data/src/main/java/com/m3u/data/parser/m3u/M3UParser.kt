package com.m3u.data.parser.m3u

import kotlinx.coroutines.flow.Flow
import java.io.InputStream

internal interface M3UParser {
    fun parse(input: InputStream): Flow<M3UData>
}
