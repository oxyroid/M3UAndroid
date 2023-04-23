@file:Suppress("unused")

package com.m3u.data.remote.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Content(
    @SerialName("_links")
    val links: Links,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("git_url")
    val gitUrl: String,
    @SerialName("html_url")
    val htmlUrl: String,
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
    @Serializable
    data class Links(
        @SerialName("git")
        val git: String,
        @SerialName("html")
        val html: String,
        @SerialName("self")
        val self: String
    )

    companion object {
        const val TYPE_DIR = "dir"
        const val TYPE_FILE = "file"
    }
}