package com.m3u.data.parser.xtream

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Xtream panels disagree with each other on the types of their own fields, and
 * a detail sheet that refuses to open because a rating arrived as a number
 * rather than a string is worse than one showing partial information.
 */
class XtreamChannelDetailsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `reads a movie response`() {
        // Shape taken from a live get_vod_info response, trimmed.
        val payload = """
            {
              "info": {
                "tmdb_id": 431112,
                "name": "Awakening the Zodiac",
                "o_name": "Awakening the Zodiac",
                "releasedate": "2017-06-09",
                "director": "Jonathan Wright",
                "actors": "Shane West, Leslie Bibb",
                "cast": "Shane West, Leslie Bibb, Matt Craven",
                "description": "A couple discovers a serial killer's reel.",
                "plot": "A couple discovers a serial killer's reel.",
                "genre": "Crime, Drama, Mystery",
                "duration_secs": 5460,
                "rating": 5
              },
              "movie_data": { "stream_id": 12345 }
            }
        """.trimIndent()

        val info = json.decodeFromString<XtreamChannelDetails>(payload).info
        requireNotNull(info)
        assertEquals("Shane West, Leslie Bibb, Matt Craven", info.cast)
        assertEquals("Jonathan Wright", info.director)
        assertEquals("Crime, Drama, Mystery", info.genre)
        assertEquals("2017-06-09", info.releaseDate)
        // Numbers where a string is expected must survive.
        assertEquals("5", info.rating)
        assertEquals("5460", info.durationSeconds)
        assertEquals("431112", info.tmdbId)
    }

    @Test
    fun `reads a series response with its own spelling of the date`() {
        val payload = """
            {
              "info": {
                "name": "The Rain",
                "cast": "Alba August, Lucas Lynggaard Tonnesen",
                "director": "",
                "genre": "Sci-Fi & Fantasy, Drama",
                "plot": "Six years after a virus wiped out most of Scandinavia.",
                "releaseDate": "2018-05-04",
                "rating": "7.4"
              },
              "episodes": {}
            }
        """.trimIndent()

        val info = json.decodeFromString<XtreamChannelDetails>(payload).info
        requireNotNull(info)
        assertEquals("2018-05-04", info.releaseDate)
        assertEquals("7.4", info.rating)
        assertEquals("Alba August, Lucas Lynggaard Tonnesen", info.cast)
    }

    @Test
    fun `falls back to actors and description when cast and plot are absent`() {
        val payload = """
            {"info": {"actors": "Some Actor", "description": "A film."}}
        """.trimIndent()

        val info = json.decodeFromString<XtreamChannelDetails>(payload).info
        requireNotNull(info)
        assertEquals("Some Actor", info.cast)
        assertEquals("A film.", info.plot)
    }

    @Test
    fun `survives an empty or absent info block`() {
        assertNull(json.decodeFromString<XtreamChannelDetails>("""{"info": {}}""").info?.plot)
        assertNull(json.decodeFromString<XtreamChannelDetails>("""{}""").info)
        // Some panels answer with an empty string where a value belongs.
        assertNull(
            json.decodeFromString<XtreamChannelDetails>(
                """{"info": {"rating": "", "tmdb_id": ""}}"""
            ).info?.rating
        )
    }

    @Test
    fun `unknown fields do not sink the payload`() {
        val payload = """
            {"info": {"plot": "A film.", "some_future_field": {"nested": [1, 2]}}}
        """.trimIndent()

        assertEquals("A film.", json.decodeFromString<XtreamChannelDetails>(payload).info?.plot)
    }
}
