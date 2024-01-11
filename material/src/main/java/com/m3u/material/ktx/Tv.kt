package com.m3u.material.ktx

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun isTvDevice(): Boolean {
    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return type == Configuration.UI_MODE_TYPE_TELEVISION
}