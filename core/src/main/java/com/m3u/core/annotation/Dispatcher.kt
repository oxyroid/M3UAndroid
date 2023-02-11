package com.m3u.core.annotation

import androidx.annotation.IntDef

annotation class Dispatcher(
    val mode: @Mode Int
) {
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
    @IntDef(Mode.Default, Mode.IO, Mode.Main)
    annotation class Mode {
        companion object {
            const val Default = 0
            const val IO = 1
            const val Main = 2
        }
    }
}
