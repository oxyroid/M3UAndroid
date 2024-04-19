package com.m3u.data.parser.epg

import java.io.InputStream

interface EpgParser {
    // TODO: Use flow API.
    suspend fun execute(
        input: InputStream,
        callback: (count: Int, total: Int) -> Unit
    ): EpgData
}