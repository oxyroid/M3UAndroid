package com.m3u.data.local.source.matcher

interface Matcher {
    fun match(url: String): Boolean
}