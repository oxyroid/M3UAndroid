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
    val playlistUrl: String? = null,
) {
    fun needsAssetUpdate(targetRevision: String): Boolean =
        status == DebugDefaultLibraryBootstrapStatus.IMPORTED &&
            revision != targetRevision
}

internal object DebugDefaultLibraryBootstrapStateCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun decodeOrNull(rawState: String): DebugDefaultLibraryBootstrapState? {
        val normalized = rawState.trim()
        legacyState(normalized)?.let { state -> return state }
        val root = runCatching {
            json.parseToJsonElement(normalized).jsonObject
        }.getOrNull() ?: return null
        if (root["schemaVersion"]?.jsonPrimitive?.intOrNull != SCHEMA_VERSION) {
            return null
        }
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
        val playlistUrl = root["playlistUrl"]?.jsonPrimitive?.contentOrNull
        val state = DebugDefaultLibraryBootstrapState(
            status = status,
            revision = revision,
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
            state.playlistUrl?.let { playlistUrl -> put("playlistUrl", playlistUrl) }
        }.toString()
    }

    private fun legacyState(
        value: String,
    ): DebugDefaultLibraryBootstrapState? =
        DebugDefaultLibraryBootstrapStatus.entries
            .singleOrNull { status -> status.serializedValue == value }
            ?.let { status -> DebugDefaultLibraryBootstrapState(status = status) }

    private fun DebugDefaultLibraryBootstrapState.isCanonical(): Boolean = when (status) {
        DebugDefaultLibraryBootstrapStatus.PENDING ->
            revision.isValidRevision() && playlistUrl == null
        DebugDefaultLibraryBootstrapStatus.IMPORTED ->
            revision.isValidRevision() && playlistUrl.isValidPlaylistUrl()
        DebugDefaultLibraryBootstrapStatus.OPTED_OUT ->
            revision == null && playlistUrl == null
    }

    private fun String?.isValidRevision(): Boolean =
        this != null && this == trim() && length in 1..MAXIMUM_REVISION_LENGTH

    private fun String?.isValidPlaylistUrl(): Boolean =
        this != null && this == trim() && length in 1..MAXIMUM_PLAYLIST_URL_LENGTH

    private const val SCHEMA_VERSION = 1
    private const val MAXIMUM_REVISION_LENGTH = 64
    private const val MAXIMUM_PLAYLIST_URL_LENGTH = 8 * 1024
}
