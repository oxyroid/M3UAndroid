package com.m3u.favorite

import com.m3u.core.wrapper.Event
import com.m3u.favorite.vo.LiveDetail

data class FavoriteState(
    val lives: List<LiveDetail> = emptyList(),
    val message: Event<String> = Event.Handled()
)
