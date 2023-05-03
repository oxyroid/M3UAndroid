@file:Suppress("unused")

package com.m3u.data.database.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// called announcement in user interface
@Entity(tableName = "posts")
data class Post(
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String,
    val standard: Int,
    val type: Int,
    // for local
    val status: Int = STATUS_UNREAD,
    @PrimaryKey
    val id: Int
) {
    @get:Ignore
    val temporal: Boolean get() = standard == -1

    companion object {
        const val LANGUAGE_ZH_CN = "zh-cn"
        const val LANGUAGE_EN_WW = "en-ww"

        const val STATUS_UNREAD = 0
        const val STATUS_READ = 1

        const val TYPE_INFO = 0
        const val TYPE_WARNING = 1
        const val TYPE_RELEASE = 2

        fun createTemporal(text: String): Post = Post(
            title = text,
            content = "",
            createdAt = 0L,
            updatedAt = 0L,
            language = "",
            standard = -1,
            type = TYPE_INFO,
            status = -1,
            id = -1
        )
    }
}

@Serializable
data class PostDTO(
    @SerialName("title")
    val title: String = "",
    @SerialName("content")
    val content: String = "",
    @SerialName("createAt")
    val createdAt: Long = 0L,
    @SerialName("updatedAt")
    val updatedAt: Long = 0L,
    @SerialName("language")
    val language: String,
    @SerialName("standard")
    val standard: Int,
    @SerialName("type")
    val type: Int,
)

fun PostDTO.toPost(
    id: Int
): Post =
    Post(
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        language = language,
        standard = standard,
        type = type,
        id = id
    )