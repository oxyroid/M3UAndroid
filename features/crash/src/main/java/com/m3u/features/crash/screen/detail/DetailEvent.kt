package com.m3u.features.crash.screen.detail

sealed interface DetailEvent {
    data class Init(val path: String) : DetailEvent
    data object Save: DetailEvent
}