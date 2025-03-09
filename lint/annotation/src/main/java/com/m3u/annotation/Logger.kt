package com.m3u.annotation


fun interface Logger {
    fun log(obj: Any?)

    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Generator(
        val name: String = "logger"
    )
}