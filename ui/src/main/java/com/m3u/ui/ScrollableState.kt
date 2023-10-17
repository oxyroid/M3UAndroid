package com.m3u.ui

import androidx.tv.foundation.lazy.grid.TvLazyGridState

val TvLazyGridState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val TvLazyGridState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
