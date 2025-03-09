package com.m3u.core.wrapper

import com.m3u.i18n.R.string
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
enum class Sort(@StringRes val resId: Int) {
    UNSPECIFIED(string.ui_sort_unspecified),
    ASC(string.ui_sort_asc),
    DESC(string.ui_sort_desc),
    RECENTLY(string.ui_sort_recently),
    MIXED(string.ui_sort_mixed)
}