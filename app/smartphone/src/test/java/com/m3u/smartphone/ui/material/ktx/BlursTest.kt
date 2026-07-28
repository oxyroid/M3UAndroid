package com.m3u.smartphone.ui.material.ktx

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class BlursTest {
    @Test
    fun `logical horizontal edges resolve to their physical ltr edges`() {
        assertEquals(
            Edge.Start,
            resolvePhysicalEdge(Edge.Start, LayoutDirection.Ltr),
        )
        assertEquals(
            Edge.End,
            resolvePhysicalEdge(Edge.End, LayoutDirection.Ltr),
        )
    }

    @Test
    fun `logical horizontal edges mirror in rtl`() {
        assertEquals(
            Edge.End,
            resolvePhysicalEdge(Edge.Start, LayoutDirection.Rtl),
        )
        assertEquals(
            Edge.Start,
            resolvePhysicalEdge(Edge.End, LayoutDirection.Rtl),
        )
    }

    @Test
    fun `vertical edges do not change with layout direction`() {
        LayoutDirection.entries.forEach { layoutDirection ->
            assertEquals(
                Edge.Top,
                resolvePhysicalEdge(Edge.Top, layoutDirection),
            )
            assertEquals(
                Edge.Bottom,
                resolvePhysicalEdge(Edge.Bottom, layoutDirection),
            )
        }
    }
}
