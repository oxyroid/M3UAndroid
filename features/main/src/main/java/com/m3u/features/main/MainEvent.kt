package com.m3u.features.main

sealed interface MainEvent {
    data class UnsubscribeFeedByUrl(val url: String) : MainEvent
    data class SetRowCount(val target: Int) : MainEvent
    data class Rename(val feedUrl: String, val target: String) : MainEvent
    object InitConfiguration : MainEvent
}