package com.m3u.material.model

import androidx.compose.ui.graphics.Color

object SugarColors {
    val Pink = Color(0xfff5cec7)
    val Red = Color(0xffe79696)
    val Yellow = Color(0xffffc988)
    val Orange = Color(0xffffb284)
    val WaterOrange = Color(0xffefbb95)
    val SugarOrange = Color(0xffe69e71)
    val Tee = Color(0xffc4c19c)
    val Green = Color(0xff87bdae)

    fun random(): Color = arrayOf(
        Pink, Red, Yellow, Orange, WaterOrange, SugarOrange, Tee, Green
    ).random()

    private val keyMap = mutableMapOf<Any, Color>()
    fun key(key: Any): Color = keyMap.getOrPut(key) { random() }
}
