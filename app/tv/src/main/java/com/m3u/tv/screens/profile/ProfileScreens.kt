package com.m3u.tv.screens.profile

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Support
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.i18n.R

enum class ProfileScreens(
    val icon: ImageVector,
    @StringRes val title: Int
) {
    Subscribe(Icons.Default.MusicNote, R.string.feat_setting_label_subscribe),
//    Appearance(Icons.Default.ColorLens, R.string.feat_setting_appearance),
    Optional(Icons.Default.Translate, R.string.feat_setting_optional_features),
    Security(Icons.Default.Security, R.string.feat_setting_security),
    HelpAndSupport(Icons.Default.Support, R.string.feat_about_title);

    operator fun invoke() = name
}
