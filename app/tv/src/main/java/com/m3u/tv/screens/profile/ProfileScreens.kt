package com.m3u.tv.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Support
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector

enum class ProfileScreens(
    val icon: ImageVector,
    private val title: String? = null,
) {
    Accounts(Icons.Default.Person),
    About(Icons.Default.Info),
    Subtitles(Icons.Default.Subtitles),
    Language(Icons.Default.Translate),
    SearchHistory(title = "Search history", icon = Icons.Default.Search),
    HelpAndSupport(title = "Help and Support", icon = Icons.Default.Support);

    operator fun invoke() = name

    val tabTitle = title ?: name
}
