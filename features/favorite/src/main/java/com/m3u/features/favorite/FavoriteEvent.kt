package com.m3u.features.favorite

sealed interface FavoriteEvent {
    data class SetRowCount(val target: Int) : FavoriteEvent
    object InitConfiguration : FavoriteEvent
}