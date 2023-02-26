package com.m3u.data.local.source.matcher.m3u

import com.m3u.data.local.source.matcher.Matcher
import java.net.URL

object M3UMatcher : Matcher {
    override fun match(url: String): Boolean = try {
        val path = URL(url).path
        path.endsWith(".m3u", ignoreCase = true) ||
                path.endsWith(".m3u8", ignoreCase = true)
    } catch (e: Exception) {
        false
    }
}