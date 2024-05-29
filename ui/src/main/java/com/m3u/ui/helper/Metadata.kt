package com.m3u.ui.helper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString

object Metadata {
    var title: AnnotatedString by mutableStateOf(AnnotatedString(""))
    var subtitle: AnnotatedString by mutableStateOf(AnnotatedString(""))
    var actions: List<Action> by mutableStateOf(emptyList())
    var fob: Fob? by mutableStateOf(null)
    var color: Color by mutableStateOf(Color.Unspecified)
    var contentColor: Color by mutableStateOf(Color.Unspecified)
}