package com.m3u.data.source.mather

import java.net.URL

interface Matcher {
    fun match(url: URL): Boolean
}