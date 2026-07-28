package com.m3u.smartphone.ui.material.ktx

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PaddingValuesTest {
    private val asymmetric = PaddingValues(start = 11.dp, end = 29.dp)

    @Test
    fun `end keeps logical end padding in ltr`() {
        val result = asymmetric.only(WindowInsetsSides.End, LayoutDirection.Ltr)

        assertEquals(0.dp, result.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(29.dp, result.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun `end keeps logical end padding in rtl`() {
        val result = asymmetric.only(WindowInsetsSides.End, LayoutDirection.Rtl)

        assertEquals(0.dp, result.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(29.dp, result.calculateEndPadding(LayoutDirection.Rtl))
    }
}
