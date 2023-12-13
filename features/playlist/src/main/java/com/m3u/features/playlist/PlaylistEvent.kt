package com.m3u.features.playlist

sealed interface PlaylistEvent {
    data class Observe(val playlistUrl: String) : PlaylistEvent
    data object Refresh : PlaylistEvent
    data class Favourite(val id: Int, val target: Boolean) : PlaylistEvent
    data class Mute(val id: Int, val target: Boolean) : PlaylistEvent
    data class SavePicture(val id: Int) : PlaylistEvent
    data object ScrollUp : PlaylistEvent
    data class Query(val text: String) : PlaylistEvent
}
