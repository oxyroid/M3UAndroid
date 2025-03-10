package com.m3u.tv.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text

@Composable
fun Error(modifier: Modifier = Modifier) {
    Text(
        text = "error",
        modifier = modifier
    )
}
