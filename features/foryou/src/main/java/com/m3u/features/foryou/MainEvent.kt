package com.m3u.features.foryou

sealed interface MainEvent {
    data class Unsubscribe(val url: String) : MainEvent
    data class Rename(val playlistUrl: String, val target: String) : MainEvent
}