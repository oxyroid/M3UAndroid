package com.m3u.core.annotation

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class ConnectTimeout {
    companion object {
        const val SHORT = 8000
        const val LONG = 20000
    }
}
