package com.m3u.data.repository.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistNetworkUrlTest {
    @Test
    fun normalizeM3uInputTrimsAndKeepsHttpScheme() {
        assertEquals(
            "http://example.com/list.m3u",
            PlaylistNetworkUrl.normalizeM3uInput("  http://example.com/list.m3u  ")
        )
    }

    @Test
    fun normalizeM3uInputAddsHttpSchemeWhenMissing() {
        assertEquals(
            "http://example.com:8080/get.php?type=m3u_plus",
            PlaylistNetworkUrl.normalizeM3uInput("example.com:8080/get.php?type=m3u_plus")
        )
    }

    @Test
    fun normalizeM3uInputKeepsTxtPlaylistUrls() {
        assertEquals(
            "https://raw.githubusercontent.com/xzhhbx/IPTV-3/master/IPTV.txt",
            PlaylistNetworkUrl.normalizeM3uInput(
                " https://raw.githubusercontent.com/xzhhbx/IPTV-3/master/IPTV.txt "
            )
        )
    }

    @Test
    fun normalizeM3uInputKeepsAndroidUris() {
        assertEquals(
            "content://playlist/live.m3u",
            PlaylistNetworkUrl.normalizeM3uInput(" content://playlist/live.m3u ")
        )
    }

    @Test
    fun normalizeAndroidFileUrlDecodesFileUrls() {
        assertEquals(
            "file:///data/user/0/com.m3u/files/My List 中文.m3u",
            PlaylistNetworkUrl.normalizeAndroidFileUrl(
                "file:///data/user/0/com.m3u/files/My%20List%20%E4%B8%AD%E6%96%87.m3u"
            )
        )
        assertEquals(
            "file:///data/user/0/com.m3u/files/A+B.m3u",
            PlaylistNetworkUrl.normalizeAndroidFileUrl("file:///data/user/0/com.m3u/files/A+B.m3u")
        )
    }

    @Test
    fun normalizeAndroidFileUrlKeepsNonFileUrls() {
        assertEquals(
            "content://playlist/My%20List.m3u",
            PlaylistNetworkUrl.normalizeAndroidFileUrl("content://playlist/My%20List.m3u")
        )
        assertEquals(
            "https://example.com/My%20List.m3u",
            PlaylistNetworkUrl.normalizeAndroidFileUrl("https://example.com/My%20List.m3u")
        )
    }

    @Test
    fun resolveInternalFileNamePrefersDisplayName() {
        assertEquals(
            "Live.m3u",
            PlaylistNetworkUrl.resolveInternalFileName(
                displayName = "Live.m3u",
                lastPathSegment = "primary:Other.m3u",
                fallbackName = "File_1"
            )
        )
    }

    @Test
    fun resolveInternalFileNameUsesUriPathWhenDisplayNameIsMissing() {
        assertEquals(
            "Live.m3u",
            PlaylistNetworkUrl.resolveInternalFileName(
                displayName = null,
                lastPathSegment = "primary:Download/Live.m3u",
                fallbackName = "File_1"
            )
        )
        assertEquals(
            "Live.m3u",
            PlaylistNetworkUrl.resolveInternalFileName(
                displayName = "",
                lastPathSegment = "primary:Live.m3u",
                fallbackName = "File_1"
            )
        )
    }

    @Test
    fun resolveInternalFileNameUsesFallbackWhenNoStableNameExists() {
        assertEquals(
            "File_1",
            PlaylistNetworkUrl.resolveInternalFileName(
                displayName = null,
                lastPathSegment = null,
                fallbackName = "File_1"
            )
        )
    }

    @Test
    fun httpFallbackForPlainHttpTlsFailureDowngradesHttpsOnly() {
        assertEquals(
            "http://example.com:8080/list.m3u",
            PlaylistNetworkUrl.httpFallbackForPlainHttpTlsFailure(
                url = "https://example.com:8080/list.m3u",
                responseMessage = "Unable to parse TLS packet header",
                responseBody = ""
            )
        )
    }

    @Test
    fun httpFallbackForPlainHttpTlsFailureDowngradesExceptionMessages() {
        assertEquals(
            "http://servizi-ita.com:8080/get.php?username=user&type=m3u_plus",
            PlaylistNetworkUrl.httpFallbackForPlainHttpTlsFailure(
                url = "https://servizi-ita.com:8080/get.php?username=user&type=m3u_plus",
                responseMessage = "Unable to parse TLS packet header",
                responseBody = ""
            )
        )
    }

    @Test
    fun httpFallbackForPlainHttpTlsFailureIgnoresNonHttpsUrls() {
        assertNull(
            PlaylistNetworkUrl.httpFallbackForPlainHttpTlsFailure(
                url = "http://example.com/list.m3u",
                responseMessage = "Unable to parse TLS packet header",
                responseBody = ""
            )
        )
    }

    @Test
    fun httpFallbackForPlainHttpTlsFailureIgnoresOtherFailures() {
        assertNull(
            PlaylistNetworkUrl.httpFallbackForPlainHttpTlsFailure(
                url = "https://example.com/list.m3u",
                responseMessage = "Not Found",
                responseBody = ""
            )
        )
    }
}
