package com.m3u.ui.helper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString

object Metadata {
    var title: AnnotatedString by mutableStateOf(AnnotatedString(""))
    var subtitle: AnnotatedString by mutableStateOf(AnnotatedString(""))
    var headlineUrl: String by mutableStateOf("")
    var headlineFraction: Float by mutableFloatStateOf(0f)
    var actions: List<Action> by mutableStateOf(emptyList())
    var fob: Fob? by mutableStateOf(null)
    var color: Color by mutableStateOf(Color.Unspecified)
    var contentColor: Color by mutableStateOf(Color.Unspecified)

    fun headlineAspectRatio(rail: Boolean): Float = if (rail) 14 / 3f
    else 7 / 3f
}