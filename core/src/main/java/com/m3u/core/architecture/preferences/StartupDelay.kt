package com.m3u.core.architecture.preferences

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class StartupDelay {
    companion object {
        const val NONE = 0L
        const val SECONDS_2 = 2_000L
        const val SECONDS_5 = 5_000L
    }
}
