package com.m3u.data.repository.parser.impl

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import com.charleskorn.kaml.encodeToStream
import com.m3u.data.repository.parser.M3UPlaylistBundle
import com.m3u.data.repository.parser.M3UPlaylistBundleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class M3UPlaylistBundleParserImpl @Inject constructor(
    private val yaml: Yaml
) : M3UPlaylistBundleParser {
    override suspend fun execute(input: InputStream): M3UPlaylistBundle = coroutineScope {
        withContext(Dispatchers.IO) {
            input.use {
                yaml.decodeFromStream<M3UPlaylistBundle>(it)
            }
        }
    }

    override suspend fun export(bundle: M3UPlaylistBundle, output: OutputStream) {
        coroutineScope {
            withContext(Dispatchers.IO) {
                output.use { yaml.encodeToStream(bundle, it) }
            }
        }
    }
}