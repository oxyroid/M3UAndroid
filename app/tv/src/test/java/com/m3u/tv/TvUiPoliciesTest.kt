package com.m3u.tv

import com.m3u.data.database.model.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvUiPoliciesTest {
    @Test
    fun `tv exposes only standalone playlist sources`() {
        assertTrue(tvSupportsPlaylistSource(DataSource.M3U))
        assertTrue(tvSupportsPlaylistSource(DataSource.Xtream))
        listOf(
            DataSource.EPG,
            DataSource.Provider,
            DataSource.Dropbox,
        ).forEach { source ->
            assertFalse(tvSupportsPlaylistSource(source), source.value)
        }
    }

    @Test
    fun `default font scale preserves the compact tv layout`() {
        assertEquals(
            TvLargeTextLayout(
                heroMinHeightDp = 288,
                heroTextWidthFraction = 0.54f,
                playlistCardMinHeightDp = 144,
                metricTileMinHeightDp = 136,
                stackEmptyLibrary = false,
                emptySetupMinHeightDp = 356,
            ),
            tvLargeTextLayout(fontScale = 1f),
        )
    }

    @Test
    fun `two hundred percent text grows clipped surfaces and stacks empty state`() {
        val layout = tvLargeTextLayout(fontScale = 2f)
        assertEquals(528, layout.heroMinHeightDp)
        assertEquals(0.78f, layout.heroTextWidthFraction, absoluteTolerance = 0.0001f)
        assertEquals(216, layout.playlistCardMinHeightDp)
        assertEquals(208, layout.metricTileMinHeightDp)
        assertTrue(layout.stackEmptyLibrary)
        assertEquals(420, layout.emptySetupMinHeightDp)
    }

    @Test
    fun `large text policy clamps invalid and extreme scale inputs`() {
        assertEquals(tvLargeTextLayout(1f), tvLargeTextLayout(0.5f))
        assertEquals(tvLargeTextLayout(3f), tvLargeTextLayout(5f))
    }

    @Test
    fun `hero physical movement follows the visual order in ltr`() {
        assertEquals(
            TvHeroAction.SECONDARY,
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.PRIMARY,
                direction = TvHorizontalDirection.RIGHT,
                isRtl = false,
            ),
        )
        assertEquals(
            TvHeroAction.PRIMARY,
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.SECONDARY,
                direction = TvHorizontalDirection.LEFT,
                isRtl = false,
            ),
        )
        assertNull(
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.PRIMARY,
                direction = TvHorizontalDirection.LEFT,
                isRtl = false,
            )
        )
        assertNull(
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.SECONDARY,
                direction = TvHorizontalDirection.RIGHT,
                isRtl = false,
            )
        )
    }

    @Test
    fun `hero physical movement mirrors the visual order in rtl`() {
        assertEquals(
            TvHeroAction.SECONDARY,
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.PRIMARY,
                direction = TvHorizontalDirection.LEFT,
                isRtl = true,
            ),
        )
        assertEquals(
            TvHeroAction.PRIMARY,
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.SECONDARY,
                direction = TvHorizontalDirection.RIGHT,
                isRtl = true,
            ),
        )
        assertNull(
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.SECONDARY,
                direction = TvHorizontalDirection.LEFT,
                isRtl = true,
            )
        )
        assertNull(
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.PRIMARY,
                direction = TvHorizontalDirection.RIGHT,
                isRtl = true,
            )
        )
    }

    @Test
    fun `leading gradient mirrors both colors and midpoint in rtl`() {
        assertEquals(
            listOf(
                0f to "leading",
                0.58f to "middle",
                1f to "trailing",
            ),
            tvLeadingGradientColorStops(
                isRtl = false,
                leading = "leading",
                middle = "middle",
                trailing = "trailing",
                middlePosition = 0.58f,
            ),
        )
        assertEquals(
            listOf(
                0f to "trailing",
                (1f - 0.58f) to "middle",
                1f to "leading",
            ),
            tvLeadingGradientColorStops(
                isRtl = true,
                leading = "leading",
                middle = "middle",
                trailing = "trailing",
                middlePosition = 0.58f,
            ),
        )
    }
}
