package com.m3u.features.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.m3u.ui.model.LocalTheme

sealed class MonoStyle(
    val prefix: String
) {
    object Input : MonoStyle(">-")
    object Error : MonoStyle("!-")
    object HighLight : MonoStyle("#-")
    object Common : MonoStyle("")

    val color: Color
        @Composable get() = when (this) {
            Input -> Color.Yellow
            Error -> LocalTheme.current.error
            HighLight -> LocalTheme.current.primary
            Common -> LocalTheme.current.surface
        }

    fun actual(text: String): String = when (this) {
        Common -> text
        Error -> text.drop(2)
        HighLight -> text.drop(2)
        Input -> text.drop(2)
    }

    companion object {
        fun get(text: String): MonoStyle {
            if (text.startsWith(Input.prefix)) return Input
            if (text.startsWith(Error.prefix)) return Error
            if (text.startsWith(HighLight.prefix)) return HighLight
            return Common
        }
    }
}

