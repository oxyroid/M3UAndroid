package com.m3u.data.parser

import java.io.InputStream
import java.io.OutputStream

typealias M3UPlaylistBundle = List<M3UPlaylist>

interface M3UPlaylistBundleParser : Parser<InputStream, M3UPlaylistBundle> {
    suspend fun export(bundle: M3UPlaylistBundle, output: OutputStream)
}