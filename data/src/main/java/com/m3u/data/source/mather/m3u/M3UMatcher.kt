package com.m3u.data.source.mather.m3u

import com.m3u.data.source.mather.Matcher
import java.net.URL

object M3UMatcher : Matcher {
    override fun match(url: URL): Boolean {
        val path = url.path
        return path.endsWith(".m3u", ignoreCase = true) ||
                path.endsWith(".m3u8", ignoreCase = true)
    }
}