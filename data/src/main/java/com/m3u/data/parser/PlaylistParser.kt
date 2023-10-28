package com.m3u.data.parser

import com.m3u.data.parser.impl.M3UData
import java.io.InputStream
import javax.inject.Qualifier

interface PlaylistParser : Parser<InputStream, List<M3UData>> {

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class Default

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class Experimental
}
