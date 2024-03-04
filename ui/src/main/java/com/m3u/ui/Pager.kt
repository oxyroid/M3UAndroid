package com.m3u.ui

import androidx.compose.foundation.pager.PageInfo
import androidx.compose.runtime.compositionLocalOf

val LocalVisiblePageInfos = compositionLocalOf<List<PageInfo>> { emptyList() }