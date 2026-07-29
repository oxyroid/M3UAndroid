package com.m3u.smartphone.startup

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DebugDefaultLibraryManifestTest {
    @Test
    fun `committed debug assets satisfy their manifest`() {
        val manifestFile = debugAsset("manifest.json")
        val manifest = DebugDefaultLibraryManifestParser.parse(
            manifestFile.readText()
        )
        val playlistBytes = debugAsset("playlist.m3u").readBytes()
        val actualSha256 = MessageDigest.getInstance("SHA-256")
            .digest(playlistBytes)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

        assertEquals(manifest.playlistSha256, actualSha256)
        DebugDefaultLibraryManifestParser.validatePlaylist(
            rawPlaylist = playlistBytes.decodeToString(),
            manifest = manifest,
        )
    }

    @Test
    fun `valid manifest and public playlist are accepted`() {
        val manifest = DebugDefaultLibraryManifestParser.parse(validManifest())

        DebugDefaultLibraryManifestParser.validatePlaylist(
            rawPlaylist = """
                #EXTM3U
                #EXTINF:-1 tvg-id="sample.one" group-title="Samples",Sample one
                https://media.example.test/one.m3u8
                #EXTINF:-1 tvg-id="sample.two" group-title="Samples",Sample two
                https://cdn.example.test/two.mp4
            """.trimIndent(),
            manifest = manifest,
        )

        assertEquals(
            setOf("sample.one", "sample.two"),
            manifest.expectedChannelIds,
        )
    }

    @Test
    fun `playlist asset cannot escape its debug directory`() {
        assertFailsWith<DebugDefaultLibraryFormatException> {
            DebugDefaultLibraryManifestParser.parse(
                validManifest(
                    playlistAsset = "default-library/../private.m3u",
                )
            )
        }
    }

    @Test
    fun `credential-bearing media URL is rejected`() {
        val manifest = DebugDefaultLibraryManifestParser.parse(validManifest())

        assertFailsWith<DebugDefaultLibraryFormatException> {
            DebugDefaultLibraryManifestParser.validatePlaylist(
                rawPlaylist = """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="sample.one",Sample one
                    https://user:password@media.example.test/one.m3u8
                    #EXTINF:-1 tvg-id="sample.two",Sample two
                    https://cdn.example.test/two.mp4
                """.trimIndent(),
                manifest = manifest,
            )
        }
    }

    @Test
    fun `signed query URL is rejected`() {
        val manifest = DebugDefaultLibraryManifestParser.parse(validManifest())

        assertFailsWith<DebugDefaultLibraryFormatException> {
            DebugDefaultLibraryManifestParser.validatePlaylist(
                rawPlaylist = """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="sample.one",Sample one
                    https://media.example.test/one.m3u8?token=secret
                    #EXTINF:-1 tvg-id="sample.two",Sample two
                    https://cdn.example.test/two.mp4
                """.trimIndent(),
                manifest = manifest,
            )
        }
    }

    @Test
    fun `playlist directives cannot carry hidden credentials`() {
        val manifest = DebugDefaultLibraryManifestParser.parse(validManifest())

        assertFailsWith<DebugDefaultLibraryFormatException> {
            DebugDefaultLibraryManifestParser.validatePlaylist(
                rawPlaylist = """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="sample.one",Sample one
                    #EXTVLCOPT:http-referrer=https://private.example.test
                    https://media.example.test/one.m3u8
                    #EXTINF:-1 tvg-id="sample.two",Sample two
                    https://cdn.example.test/two.mp4
                """.trimIndent(),
                manifest = manifest,
            )
        }
    }

    private fun debugAsset(name: String): File {
        val candidates = listOf(
            File("src/debug/assets/default-library/$name"),
            File("app/smartphone/src/debug/assets/default-library/$name"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error(
                "Debug default library asset not found from " +
                    File(".").absoluteFile.normalize().path
            )
    }

    private fun validManifest(
        playlistAsset: String = "default-library/playlist.m3u",
    ): String = """
        {
          "schemaVersion": 1,
          "revision": "test.1",
          "title": "Test streams",
          "playlistAsset": "$playlistAsset",
          "playlistSha256": "${"a".repeat(64)}",
          "expectedChannelIds": ["sample.one", "sample.two"],
          "allowedMediaHosts": [
            "media.example.test",
            "cdn.example.test"
          ]
        }
    """.trimIndent()
}
