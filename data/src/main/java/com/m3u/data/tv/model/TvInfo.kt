package com.m3u.data.tv.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.m3u.core.architecture.Abi
import kotlinx.serialization.Serializable

@Keep
@Serializable
@Immutable
data class TvInfo(
    val model: String,
    val version: Int,
    val snapshot: Boolean,
    val abi: Abi,
    val allowUpdatedPackage: Boolean = false
)