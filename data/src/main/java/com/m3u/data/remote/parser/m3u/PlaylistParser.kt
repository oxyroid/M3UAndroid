package com.m3u.data.remote.parser.m3u

import com.m3u.data.remote.parser.Parser
import java.io.InputStream

interface PlaylistParser : Parser<InputStream, List<M3UData>>