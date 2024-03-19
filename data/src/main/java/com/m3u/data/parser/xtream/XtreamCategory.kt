package com.m3u.data.parser.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamCategory(
    @SerialName("category_id")
    val categoryId: Int?,
    @SerialName("category_name")
    val categoryName: String?,
    @SerialName("parent_id")
    val parentId: Int?
)