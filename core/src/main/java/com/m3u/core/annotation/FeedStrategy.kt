package com.m3u.core.annotation

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class FeedStrategy {
    companion object {
        const val ALL = 0
        const val SKIP_FAVORITE = 1
    }
}

typealias OnFeedStrategy = () -> Unit