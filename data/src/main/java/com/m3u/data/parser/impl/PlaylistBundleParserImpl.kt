package com.m3u.data.parser.impl

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import com.charleskorn.kaml.encodeToStream
import com.m3u.data.parser.PlaylistBundle
import com.m3u.data.parser.PlaylistBundleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class PlaylistBundleParserImpl @Inject constructor(
    private val yaml: Yaml
) : PlaylistBundleParser {
    override suspend fun execute(input: InputStream): PlaylistBundle = coroutineScope {
        withContext(Dispatchers.IO) {
            input.use {
                yaml.decodeFromStream<PlaylistBundle>(it)
            }
        }
    }

    override suspend fun export(bundle: PlaylistBundle, output: OutputStream) {
        coroutineScope {
            withContext(Dispatchers.IO) {
                output.use { yaml.encodeToStream(bundle, it) }
            }
        }
    }
}