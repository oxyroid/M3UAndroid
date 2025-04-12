package com.m3u.core.foundation.ui

import androidx.compose.ui.graphics.Color

enum class SugarColors(val color: Color, val contentColor: Color) {
    Pink(Color(0xfff5cec7), Color.Black),
    Red(Color(0xffe79696), Color.Black),
    Yellow(Color(0xffffc988), Color.Black),
    Orange(Color(0xffffb284), Color.Black),
    WaterOrange(Color(0xffefbb95), Color.Black),
    SugarOrange(Color(0xffe69e71), Color.Black),
    Tee(Color(0xffc4c19c), Color.Black),
    Green(Color(0xff87bdae), Color.Black);
    operator fun component1(): Color = color
    operator fun component2(): Color = contentColor
}