package com.m3u.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform