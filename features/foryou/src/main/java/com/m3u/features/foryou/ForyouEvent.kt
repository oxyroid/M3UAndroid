package com.m3u.features.foryou

sealed interface ForyouEvent {
    data class Unsubscribe(val url: String) : ForyouEvent
    data class Rename(val playlistUrl: String, val target: String) : ForyouEvent
}