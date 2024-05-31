@file:Suppress("unused")

package com.m3u.material.ktx

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState

val LazyListState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val LazyListState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

val LazyGridState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val LazyGridState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

val LazyStaggeredGridState.isAtTop
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

val LazyStaggeredGridState.isScrolled
    get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
