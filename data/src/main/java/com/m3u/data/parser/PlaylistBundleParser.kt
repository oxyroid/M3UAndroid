package com.m3u.data.parser

import java.io.InputStream
import java.io.OutputStream

typealias PlaylistBundle = List<Playlist>

interface PlaylistBundleParser : Parser<InputStream, PlaylistBundle> {
    suspend fun export(bundle: PlaylistBundle, output: OutputStream)
}