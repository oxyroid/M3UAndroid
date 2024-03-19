package com.m3u.data.parser.m3u

import com.m3u.data.parser.Parser
import java.io.InputStream

internal interface M3UParser : Parser<InputStream, List<M3UData>>
