package com.m3u.core.architecture.preferences

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class UnseensMilliseconds {
    companion object {
        const val DAYS_3 = 3L * 24 * 60 * 60 * 1000
        const val DAYS_7 = 7L * 24 * 60 * 60 * 1000
        const val DAYS_30 = 30L * 24 * 60 * 60 * 1000
        const val NEVER = Long.MAX_VALUE
    }
}
