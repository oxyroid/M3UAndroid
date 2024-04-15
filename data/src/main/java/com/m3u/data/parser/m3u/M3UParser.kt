package com.m3u.data.parser.m3u

import java.io.InputStream

internal interface M3UParser {
    // TODO: convert to flow api.
    suspend fun execute(
        input: InputStream,
        callback: (count: Int, total: Int) -> Unit = { _, _ -> }
    ): List<M3UData>
}
