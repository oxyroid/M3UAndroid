package com.m3u.smartphone.startup

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class DebugDefaultLibraryManifest(
    val revision: String,
    val title: String,
    val playlistAsset: String,
    val playlistSha256: String,
    val expectedChannelIds: Set<String>,
    val allowedMediaHosts: Set<String>,
)

internal object DebugDefaultLibraryManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }
    private val channelIdPattern = Regex("""\btvg-id="([^"]+)"""")
    private val assetIdPattern = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    private val sha256Pattern = Regex("[0-9a-f]{64}")
    private val hostPattern = Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")

    fun parse(rawManifest: String): DebugDefaultLibraryManifest {
        val root = try {
            json.parseToJsonElement(rawManifest).jsonObject
        } catch (error: Exception) {
            throw DebugDefaultLibraryFormatException(
                message = "The bundled default library manifest is not valid JSON",
                cause = error,
            )
        }
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
        formatCheck(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported bundled default library schema: $schemaVersion"
        }
        val revision = root.requiredString("revision")
        formatCheck(revision.length in 1..64) {
            "The bundled default library revision is invalid"
        }
        val title = root.requiredString("title")
        formatCheck(title == title.trim() && title.length in 1..120) {
            "The bundled default library title is invalid"
        }
        val playlistAsset = root.requiredString("playlistAsset")
        formatCheck(
            playlistAsset.startsWith("$ASSET_DIRECTORY/") &&
                playlistAsset.endsWith(".m3u") &&
                '\\' !in playlistAsset &&
                playlistAsset.split('/').none { segment ->
                    segment.isBlank() || segment == "." || segment == ".."
                }
        ) {
            "The bundled playlist asset path is invalid"
        }
        val playlistSha256 = root.requiredString("playlistSha256")
        formatCheck(sha256Pattern.matches(playlistSha256)) {
            "The bundled playlist SHA-256 is invalid"
        }
        val expectedChannelIds = root.requiredStringSet("expectedChannelIds")
        formatCheck(
            expectedChannelIds.isNotEmpty() &&
                expectedChannelIds.size <= MAXIMUM_CHANNEL_COUNT &&
                expectedChannelIds.all(assetIdPattern::matches)
        ) {
            "The bundled playlist channel identifiers are invalid"
        }
        val allowedMediaHosts = root.requiredStringSet("allowedMediaHosts")
            .mapTo(mutableSetOf()) { host -> host.lowercase() }
        formatCheck(
            allowedMediaHosts.isNotEmpty() &&
                allowedMediaHosts.size <= MAXIMUM_HOST_COUNT &&
                allowedMediaHosts.all(hostPattern::matches)
        ) {
            "The bundled playlist media hosts are invalid"
        }
        return DebugDefaultLibraryManifest(
            revision = revision,
            title = title,
            playlistAsset = playlistAsset,
            playlistSha256 = playlistSha256,
            expectedChannelIds = expectedChannelIds,
            allowedMediaHosts = allowedMediaHosts,
        )
    }

    fun validatePlaylist(
        rawPlaylist: String,
        manifest: DebugDefaultLibraryManifest,
    ) {
        val lines = rawPlaylist.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        formatCheck(lines.firstOrNull() == "#EXTM3U") {
            "The bundled playlist must start with #EXTM3U"
        }
        val entryLines = lines.drop(1)
        formatCheck(entryLines.size == manifest.expectedChannelIds.size * 2) {
            "The bundled playlist must contain one EXTINF and URL per channel"
        }
        val entries = entryLines.chunked(2)
        val channelIds = entries.map { (metadata, _) ->
            formatCheck(metadata.startsWith("#EXTINF:", ignoreCase = true)) {
                "The bundled playlist contains an unsupported directive"
            }
            val identifiers = channelIdPattern.findAll(metadata)
                .map { match -> match.groupValues[1] }
                .toList()
            formatCheck(identifiers.size == 1) {
                "Each bundled playlist entry must have one tvg-id"
            }
            identifiers.single()
        }
        formatCheck(
            channelIds.size == channelIds.toSet().size &&
                channelIds.toSet() == manifest.expectedChannelIds
        ) {
            "The bundled playlist channel identifiers do not match its manifest"
        }
        val mediaUrls = entries.map { (_, mediaUrl) -> mediaUrl }
        mediaUrls.forEach { rawUrl ->
            formatCheck(!rawUrl.startsWith('#')) {
                "The bundled playlist contains an unsupported directive"
            }
            val uri = try {
                URI(rawUrl)
            } catch (error: Exception) {
                throw DebugDefaultLibraryFormatException(
                    message = "The bundled playlist contains an invalid media URL",
                    cause = error,
                )
            }
            formatCheck(
                uri.scheme.equals("https", ignoreCase = true) &&
                    uri.rawUserInfo == null &&
                    uri.rawQuery == null &&
                    uri.rawFragment == null &&
                    uri.port in setOf(-1, 443) &&
                    uri.host?.lowercase() in manifest.allowedMediaHosts
            ) {
                "The bundled playlist contains a disallowed media URL"
            }
        }
    }

    private fun JsonObject.requiredString(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull
            ?: throw DebugDefaultLibraryFormatException(
                "Missing bundled default library field: $key"
            )

    private fun JsonObject.requiredStringSet(key: String): Set<String> {
        val values = try {
            getValue(key).jsonArray.map { element ->
                element.jsonPrimitive.content
            }
        } catch (error: Exception) {
            throw DebugDefaultLibraryFormatException(
                message = "Invalid bundled default library field: $key",
                cause = error,
            )
        }
        formatCheck(values.size == values.toSet().size) {
            "Bundled default library values must be unique: $key"
        }
        return values.toSet()
    }

    private const val SUPPORTED_SCHEMA_VERSION = 1
    private const val ASSET_DIRECTORY = "default-library"
    private const val MAXIMUM_CHANNEL_COUNT = 64
    private const val MAXIMUM_HOST_COUNT = 16
}

internal class DebugDefaultLibraryFormatException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

private inline fun formatCheck(
    condition: Boolean,
    lazyMessage: () -> String,
) {
    if (!condition) {
        throw DebugDefaultLibraryFormatException(lazyMessage())
    }
}
