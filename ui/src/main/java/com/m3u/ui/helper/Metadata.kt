package com.m3u.ui.helper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Metadata {
    var title: String by mutableStateOf("")
    var actions: List<Action> by mutableStateOf(emptyList())
    var fob: Fob? by mutableStateOf(null)
}