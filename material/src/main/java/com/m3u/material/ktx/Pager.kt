package com.m3u.material.ktx

import androidx.compose.foundation.pager.PagerState
import kotlin.math.absoluteValue

fun PagerState.pageOffset(page: Int): Float {
    return ((currentPage - page) + currentPageOffsetFraction).absoluteValue
}