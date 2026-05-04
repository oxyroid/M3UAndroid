package com.m3u.core.foundation.util.context

import android.content.res.Configuration

val Configuration.isPortraitMode: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT

val Configuration.isDarkMode: Boolean
    get() = uiMode == Configuration.UI_MODE_NIGHT_YES
