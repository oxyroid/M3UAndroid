package com.m3u.core.architecture.preferences

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class ClipMode {
    companion object {
        const val ADAPTIVE = 0
        const val CLIP = 1
        const val STRETCHED = 2
    }
}
