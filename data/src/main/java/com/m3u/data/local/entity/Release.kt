package com.m3u.data.local.entity

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class Release(
    val assets: List<Asset>,
    val assets_url: String,
    val author: User,
    val body: String,
    val created_at: String,
    val draft: Boolean,
    val html_url: String,
    val id: Int,
    val name: String,
    val node_id: String,
    val prerelease: Boolean,
    val published_at: String,
    val tag_name: String,
    val tarball_url: String,
    val target_commitish: String,
    val upload_url: String,
    val url: String,
    val zipball_url: String
) {
    @Keep
    @Serializable
    data class Asset(
        val browser_download_url: String,
        val content_type: String,
        val created_at: String,
        val download_count: Int,
        val id: Int,
        val name: String,
        val node_id: String,
        val size: Int,
        val state: String,
        val updated_at: String,
        val uploader: User,
        val url: String
    )

    @Keep
    @Serializable
    data class User(
        val avatar_url: String,
        val events_url: String,
        val followers_url: String,
        val following_url: String,
        val gists_url: String,
        val gravatar_id: String,
        val html_url: String,
        val id: Int,
        val login: String,
        val node_id: String,
        val organizations_url: String,
        val received_events_url: String,
        val repos_url: String,
        val site_admin: Boolean,
        val starred_url: String,
        val subscriptions_url: String,
        val type: String,
        val url: String
    )
}