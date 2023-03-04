package com.m3u.features.main

sealed interface MainEvent {
    data class UnsubscribeFeedByUrl(val url: String): MainEvent
}