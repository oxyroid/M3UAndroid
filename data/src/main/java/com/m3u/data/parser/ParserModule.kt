@file:Suppress("unused")

package com.m3u.data.parser

import com.m3u.data.parser.internal.M3UParserImpl
import com.m3u.data.parser.internal.XtreamParserImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface ParserModule {
    @Binds
    fun bindM3UParser(parser: M3UParserImpl): M3UParser

    @Binds
    fun bindXtreamParser(parser: XtreamParserImpl): XtreamParser
}
