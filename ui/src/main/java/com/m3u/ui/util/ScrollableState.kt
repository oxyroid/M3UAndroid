@file:Suppress("unused")

package com.m3u.ui.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.tv.foundation.lazy.grid.TvLazyGridState

val LazyListState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val LazyListState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

val LazyGridState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val LazyGridState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

val TvLazyGridState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val TvLazyGridState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
