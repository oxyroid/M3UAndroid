package com.m3u.ui.components.basic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun M3ULocalProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(
//        LocalTextStyle provides TextStyle.Default.copy(
//            fontFamily = FontFamily(Font(R.font.standard))
//        )
    ) {
        content()
    }
}