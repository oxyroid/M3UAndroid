package com.m3u.data.parser.epg

import androidx.compose.runtime.Immutable

@Immutable
data class EpgData(
    val channels: List<EpgChannel>,
    val programmes: List<EpgProgramme>
)

data class EpgChannel(
    val id: String,
    val displayName: String? = null,
    val icon: String? = null,
    val url: String? = null
)

data class EpgProgramme(
    val channel: String,
    val start: String? = null,
    val stop: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val icon: String? = null,
    val categories: List<String>
)
