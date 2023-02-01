package com.m3u.features.main

import com.m3u.core.wrapper.Event
import com.m3u.features.main.model.SubDetail

data class MainState(
    val loading: Boolean = false,
    val details: List<SubDetail> = emptyList(),
    val message: Event<String> = Event.Handled(),
)