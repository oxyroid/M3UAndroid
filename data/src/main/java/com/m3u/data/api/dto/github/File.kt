@file:Suppress("unused")

package com.m3u.data.api.dto.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class File(
    @SerialName("_links")
    val links: Links,
    @SerialName("download_url")
    val downloadUrl: String = "",
    @SerialName("git_url")
    val gitUrl: String = "",
    @SerialName("html_url")
    val htmlUrl: String = "",
    @SerialName("name")
    val name: String,
    @SerialName("path")
    val path: String,
    @SerialName("sha")
    val sha: String,
    @SerialName("size")
    val size: Int,
    @SerialName("type")
    val type: String,
    @SerialName("url")
    val url: String
) {
    companion object {
        const val TYPE_DIR = "dir"
        const val TYPE_FILE = "file"
    }
}
