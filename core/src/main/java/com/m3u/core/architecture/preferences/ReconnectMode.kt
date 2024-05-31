package com.m3u.core.architecture.preferences

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class ReconnectMode {
    companion object {
        const val NO = 0
        const val RETRY = 1
        const val RECONNECT = 2
    }
}
