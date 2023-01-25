package com.m3u.ui.components.basic

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.m3u.ui.model.Typography

@Composable
fun M3ULocalProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(
//        LocalTextStyle provides TextStyle.Default.copy(
//            fontFamily = FontFamily(Font(R.font.standard))
//        )
    ) {
        MaterialTheme(
            typography = Typography,
            content = content
        )
    }
}