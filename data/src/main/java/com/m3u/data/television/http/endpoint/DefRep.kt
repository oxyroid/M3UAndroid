package com.m3u.data.television.http.endpoint

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DefRep(
    val success: Boolean,
    val reason: String? = null
)