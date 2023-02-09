package com.m3u.data.source.mather

interface Matcher {
    fun match(url: String): Boolean
}