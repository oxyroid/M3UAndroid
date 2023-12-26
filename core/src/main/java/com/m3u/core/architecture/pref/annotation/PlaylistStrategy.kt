package com.m3u.core.architecture.pref.annotation

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class PlaylistStrategy {
    companion object {
        const val ALL = 0
        const val SKIP_FAVORITE = 1
    }
}
