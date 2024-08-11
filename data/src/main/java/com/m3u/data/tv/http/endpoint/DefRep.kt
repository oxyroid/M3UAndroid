package com.m3u.data.tv.http.endpoint

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DefRep(
    val result: Boolean,
    val reason: String? = null
)