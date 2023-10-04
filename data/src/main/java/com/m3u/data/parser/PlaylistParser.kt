package com.m3u.data.parser

import com.m3u.data.parser.impl.M3UData
import java.io.InputStream
import java.lang.RuntimeException

interface PlaylistParser : Parser<InputStream, List<M3UData>>

object ConfusingFormatError: RuntimeException()
