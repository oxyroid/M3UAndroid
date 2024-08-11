package com.m3u.core.util.context

import android.content.res.Configuration

val Configuration.isPortraitMode: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT

val Configuration.isDarkMode: Boolean
    get() = uiMode == Configuration.UI_MODE_NIGHT_YES

val Configuration.tv: Boolean
    get() {
        val type = uiMode and Configuration.UI_MODE_TYPE_MASK
        return type == Configuration.UI_MODE_TYPE_TELEVISION
    }