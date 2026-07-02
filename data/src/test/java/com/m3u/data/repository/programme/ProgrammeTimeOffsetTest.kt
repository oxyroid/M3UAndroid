package com.m3u.data.repository.programme

import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammeTimeOffsetTest {
    @Test
    fun positiveOffsetShiftsProgrammeLater() {
        val programme = programme(start = 1_000L, end = 2_000L)

        val shifted = programme.withTimeOffset(3_600_000L)

        assertEquals(3_601_000L, shifted.start)
        assertEquals(3_602_000L, shifted.end)
    }

    @Test
    fun negativeOffsetShiftsProgrammeEarlier() {
        val programme = programme(start = 3_601_000L, end = 3_602_000L)

        val shifted = programme.withTimeOffset(-3_600_000L)

        assertEquals(1_000L, shifted.start)
        assertEquals(2_000L, shifted.end)
    }

    @Test
    fun zeroOffsetKeepsSameProgrammeInstance() {
        val programme = programme(start = 1_000L, end = 2_000L)

        assertSame(programme, programme.withTimeOffset(0L))
    }

    @Test
    fun offsetShiftsProgrammeRange() {
        val range = ProgrammeRange(start = 1_000L, end = 2_000L)

        val shifted = range.withTimeOffset(3_600_000L)

        assertEquals(3_601_000L, shifted.start)
        assertEquals(3_602_000L, shifted.end)
    }

    @Test
    fun buildProgrammeRelationIdsIncludesTrimmedCaseVariants() {
        val relationIds = buildProgrammeRelationIds(
            relationId = " cctv1 ",
            title = "CCTV 1"
        )

        assertEquals(
            listOf(
                "cctv1",
                "CCTV1",
                "CCTV 1",
                "cctv 1"
            ),
            relationIds
        )
    }

    @Test
    fun gzipEpgResponseDetectedFromContentEncoding() {
        assertTrue(
            isGzipEpgResponse(
                contentType = "application/xml",
                contentEncoding = "gzip",
                lastPathSegment = "epg.xml"
            )
        )
    }

    @Test
    fun gzipEpgResponseDetectedFromContentTypeIgnoringCase() {
        assertTrue(
            isGzipEpgResponse(
                contentType = "Application/GZip",
                contentEncoding = "",
                lastPathSegment = "epg.xml"
            )
        )
    }

    @Test
    fun gzipEpgResponseDetectedFromPathWhenMimeTypeIsWrong() {
        assertTrue(
            isGzipEpgResponse(
                contentType = "text/plain",
                contentEncoding = "",
                lastPathSegment = "epg.xml.gz"
            )
        )
    }

    @Test
    fun plainEpgResponseIsNotGzip() {
        assertFalse(
            isGzipEpgResponse(
                contentType = "application/xml",
                contentEncoding = "",
                lastPathSegment = "epg.xml"
            )
        )
    }

    private fun programme(
        start: Long,
        end: Long
    ): Programme = Programme(
        channelId = "channel",
        epgUrl = "https://example.com/epg.xml",
        start = start,
        end = end,
        title = "Programme",
        description = "Description",
        categories = emptyList()
    )
}
