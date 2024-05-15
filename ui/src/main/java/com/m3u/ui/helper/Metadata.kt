package com.m3u.ui.helper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object Metadata {
    var title: String by mutableStateOf("")
    var actions: List<Action> by mutableStateOf(emptyList())
    var fob: Fob? by mutableStateOf(null)
    var color: Color by mutableStateOf(Color.Unspecified)
    var contentColor: Color by mutableStateOf(Color.Unspecified)
}