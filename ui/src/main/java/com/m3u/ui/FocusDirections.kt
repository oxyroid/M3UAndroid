package com.m3u.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import com.m3u.data.tv.model.RemoteDirection

@OptIn(ExperimentalComposeUiApi::class)
fun RemoteDirection.asFocusDirection(): FocusDirection {
    return when (this) {
        RemoteDirection.LEFT -> FocusDirection.Left
        RemoteDirection.RIGHT -> FocusDirection.Right
        RemoteDirection.UP -> FocusDirection.Up
        RemoteDirection.DOWN -> FocusDirection.Down
        RemoteDirection.ENTER -> FocusDirection.Enter
        RemoteDirection.EXIT -> FocusDirection.Exit
    }
}