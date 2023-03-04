package com.m3u.core.annotation

import androidx.annotation.IntDef

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
@IntDef(FeedStrategy.ALL, FeedStrategy.SKIP_FAVORITE)
annotation class FeedStrategy {
    companion object {
        const val ALL = 0
        const val SKIP_FAVORITE = 1
    }
}

typealias OnFeedStrategy = (@FeedStrategy Int) -> Unit