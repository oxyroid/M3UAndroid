package com.m3u.features.setting

import android.net.Uri
import com.m3u.data.database.entity.Stream

data class SettingState(
    val versionName: String = "",
    val versionCode: Int = -1,
    val title: String = "",
    val url: String = "",
    val uri: Uri = Uri.EMPTY,
    val banneds: List<Stream> = emptyList(),
    val localStorage: Boolean = false
) {
    val actualUrl
        get() = if (localStorage) {
            uri.takeIf { uri != Uri.EMPTY }?.toString()
        } else {
            url.ifEmpty { null }
        }
}