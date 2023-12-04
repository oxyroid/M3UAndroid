package com.m3u.data.parser

import com.m3u.data.parser.model.M3UData
import java.io.InputStream
import javax.inject.Qualifier

typealias Playlist = List<M3UData>

interface PlaylistParser : Parser<InputStream, Playlist> {

    val engine: String

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class Default

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class BjoernPetersen
}
