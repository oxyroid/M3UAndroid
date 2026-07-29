package com.m3u.business.playlist.configuration

import com.m3u.data.worker.playlistWorkTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PlaylistConfigurationNavigationTest {
    @Test
    fun `route contains only a stable playlist reference`() {
        val username = "private-user"
        val password = "private-password"
        val token = "private-token"
        val playlistUrl =
            "https://example.com/get.php?username=$username&password=$password&token=$token"

        val route = PlaylistConfigurationNavigation
            .createPlaylistConfigurationRoute(playlistUrl)

        assertEquals(
            "playlist_configuration_route?playlist_url=${playlistWorkTag(playlistUrl)}",
            route,
        )
        assertFalse(playlistUrl in route)
        assertFalse(username in route)
        assertFalse(password in route)
        assertFalse(token in route)
    }

    @Test
    fun `hashed argument resolves to the current playlist url`() {
        val expectedUrl = "https://example.com/playlist.m3u"

        val resolved = resolvePlaylistConfigurationUrl(
            playlistUrls = listOf("https://example.com/other.m3u", expectedUrl),
            routeArgument = playlistConfigurationReference(expectedUrl),
        )

        assertEquals(expectedUrl, resolved)
    }

    @Test
    fun `legacy raw url argument still resolves once`() {
        val legacyRawUrl =
            "https://example.com/get.php?username=legacy&password=secret"

        val resolved = resolvePlaylistConfigurationUrl(
            playlistUrls = listOf(legacyRawUrl),
            routeArgument = legacyRawUrl,
        )

        assertEquals(legacyRawUrl, resolved)
    }

    @Test
    fun `unknown argument does not resolve`() {
        val resolved = resolvePlaylistConfigurationUrl(
            playlistUrls = listOf("https://example.com/playlist.m3u"),
            routeArgument = "playlist-work:unknown",
        )

        assertNull(resolved)
    }
}
