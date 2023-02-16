package com.m3u.data.source.matcher

interface Matcher {
    fun match(url: String): Boolean
}