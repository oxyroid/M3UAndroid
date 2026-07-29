package com.m3u.smartphone.startup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal enum class DebugDefaultLibraryBootstrapStatus(
    val serializedValue: String,
) {
    PENDING("pending"),
    IMPORTED("imported"),
    OPTED_OUT("opted-out"),
}

internal data class DebugDefaultLibraryBootstrapState(
    val status: DebugDefaultLibraryBootstrapStatus,
    val revision: String? = null,
    val playlistSha256: String? = null,
    val playlistUrl: String? = null,
) {
    fun needsAssetUpdate(
        targetRevision: String,
        targetPlaylistSha256: String,
    ): Boolean =
        status == DebugDefaultLibraryBootstrapStatus.IMPORTED &&
            (
                revision != targetRevision ||
                    playlistSha256 != targetPlaylistSha256
            )
}

internal object DebugDefaultLibraryBootstrapStateCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun decodeOrNull(rawState: String): DebugDefaultLibraryBootstrapState? {
        val normalized = rawState.trim()
        val root = runCatching {
            json.parseToJsonElement(normalized).jsonObject
        }.getOrNull() ?: return null
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
        if (schemaVersion != SCHEMA_VERSION) return null
        val status = root["status"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { value ->
                DebugDefaultLibraryBootstrapStatus.entries.singleOrNull { status ->
                    status.serializedValue == value
                }
            }
            ?: return null
        val revision = root["revision"]?.jsonPrimitive?.contentOrNull
        val playlistSha256 =
            root["playlistSha256"]?.jsonPrimitive?.contentOrNull
        val playlistUrl = root["playlistUrl"]?.jsonPrimitive?.contentOrNull
        val state = DebugDefaultLibraryBootstrapState(
            status = status,
            revision = revision,
            playlistSha256 = playlistSha256,
            playlistUrl = playlistUrl,
        )
        return state.takeIf { candidate -> candidate.isCanonical() }
    }

    fun encode(state: DebugDefaultLibraryBootstrapState): String {
        require(state.isCanonical()) {
            "The bundled default library bootstrap state is invalid"
        }
        return buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("status", state.status.serializedValue)
            state.revision?.let { revision -> put("revision", revision) }
            state.playlistSha256?.let { sha256 ->
                put("playlistSha256", sha256)
            }
            state.playlistUrl?.let { playlistUrl -> put("playlistUrl", playlistUrl) }
        }.toString()
    }

    private fun DebugDefaultLibraryBootstrapState.isCanonical(): Boolean = when (status) {
        DebugDefaultLibraryBootstrapStatus.PENDING ->
            revision.isValidRevision() &&
                playlistSha256.isValidPlaylistSha256() &&
                playlistUrl == null
        DebugDefaultLibraryBootstrapStatus.IMPORTED ->
            revision.isValidRevision() &&
                playlistSha256.isValidPlaylistSha256() &&
                playlistUrl.isValidPlaylistUrl()
        DebugDefaultLibraryBootstrapStatus.OPTED_OUT ->
            revision == null && playlistSha256 == null && playlistUrl == null
    }

    private fun String?.isValidRevision(): Boolean =
        this != null && this == trim() && length in 1..MAXIMUM_REVISION_LENGTH

    private fun String?.isValidPlaylistUrl(): Boolean =
        this != null && this == trim() && length in 1..MAXIMUM_PLAYLIST_URL_LENGTH

    private fun String?.isValidPlaylistSha256(): Boolean =
        this != null && SHA256_PATTERN.matches(this)

    private const val SCHEMA_VERSION = 1
    private const val MAXIMUM_REVISION_LENGTH = 64
    private const val MAXIMUM_PLAYLIST_URL_LENGTH = 8 * 1024
    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
}
