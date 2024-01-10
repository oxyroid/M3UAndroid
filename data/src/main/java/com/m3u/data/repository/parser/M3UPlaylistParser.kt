package com.m3u.data.repository.parser

import com.m3u.data.repository.parser.model.M3UData
import java.io.InputStream
import javax.inject.Qualifier

typealias M3UPlaylist = List<M3UData>

interface M3UPlaylistParser : Parser<InputStream, M3UPlaylist> {

    val engine: String

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class Default

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class BjoernPetersen
}
