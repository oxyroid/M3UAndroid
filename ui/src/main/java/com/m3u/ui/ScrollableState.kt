package com.m3u.ui

import androidx.tv.foundation.lazy.grid.TvLazyGridState
import androidx.tv.foundation.lazy.list.TvLazyListState

val TvLazyGridState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val TvLazyGridState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

val TvLazyListState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val TvLazyListState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
