package com.m3u.core.architecture.dispatcher

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

@Qualifier
@Retention(RUNTIME)
annotation class Dispatcher(val dispatcher: M3uDispatchers)

enum class M3uDispatchers {
    Default,
    IO,
    Main
}