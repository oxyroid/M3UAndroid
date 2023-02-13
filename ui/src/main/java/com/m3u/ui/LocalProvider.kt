package com.m3u.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.m3u.ui.model.*

@Composable
fun M3ULocalProvider(content: @Composable () -> Unit) {
    val theme = if (isSystemInDarkTheme()) NightTheme
    else DayTheme
    val background = Background(
        color = theme.background
    )
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isSystemInDarkTheme()

    DisposableEffect(systemUiController, useDarkIcons) {
        systemUiController.setSystemBarsColor(
            color = Color.Transparent,
            darkIcons = useDarkIcons
        )
        onDispose {}
    }
    CompositionLocalProvider(
        LocalTheme provides theme,
        LocalBackground provides background
    ) {
        MaterialTheme(
            typography = Typography,
            content = content
        )
    }
}