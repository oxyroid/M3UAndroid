package com.m3u.smartphone.ui.navigation

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationPolicyTest {
    @Test
    fun `window widths below 600 dp use an overlaid bottom navigation`() {
        assertEquals(AppNavigationMode.BottomOverlay, resolveAppNavigationMode(320.dp))
        assertEquals(AppNavigationMode.BottomOverlay, resolveAppNavigationMode(599.dp))
    }

    @Test
    fun `window widths at or above 600 dp use a side rail`() {
        assertEquals(AppNavigationMode.SideRail, resolveAppNavigationMode(600.dp))
        assertEquals(AppNavigationMode.SideRail, resolveAppNavigationMode(840.dp))
    }

    @Test
    fun `bottom edge blur is retained only for the side rail layout`() {
        assertFalse(shouldShowBottomEdgeBlur(AppNavigationMode.BottomOverlay))
        assertTrue(shouldShowBottomEdgeBlur(AppNavigationMode.SideRail))
    }

    @Test
    fun `bottom navigation is shown only on an unobstructed compact top-level route`() {
        assertTrue(
            shouldShowBottomNavigation(
                mode = AppNavigationMode.BottomOverlay,
                isTopLevelRoute = true,
                isSearchActive = false,
                isImeVisible = false,
            )
        )

        assertFalse(
            shouldShowBottomNavigation(
                mode = AppNavigationMode.BottomOverlay,
                isTopLevelRoute = false,
                isSearchActive = false,
                isImeVisible = false,
            )
        )
        assertFalse(
            shouldShowBottomNavigation(
                mode = AppNavigationMode.BottomOverlay,
                isTopLevelRoute = true,
                isSearchActive = true,
                isImeVisible = false,
            )
        )
        assertFalse(
            shouldShowBottomNavigation(
                mode = AppNavigationMode.BottomOverlay,
                isTopLevelRoute = true,
                isSearchActive = false,
                isImeVisible = true,
            )
        )
        assertFalse(
            shouldShowBottomNavigation(
                mode = AppNavigationMode.SideRail,
                isTopLevelRoute = true,
                isSearchActive = false,
                isImeVisible = false,
            )
        )
    }

    @Test
    fun `backdrop capture follows glass support and the full visibility transition`() {
        assertFalse(
            shouldCaptureNavigationBackdrop(
                mode = AppNavigationMode.BottomOverlay,
                supportsBackdropEffects = false,
                isNavigationCurrentlyVisible = true,
                isNavigationTargetVisible = true,
            )
        )
        assertTrue(
            shouldCaptureNavigationBackdrop(
                mode = AppNavigationMode.BottomOverlay,
                supportsBackdropEffects = true,
                isNavigationCurrentlyVisible = false,
                isNavigationTargetVisible = true,
            )
        )
        assertTrue(
            shouldCaptureNavigationBackdrop(
                mode = AppNavigationMode.BottomOverlay,
                supportsBackdropEffects = true,
                isNavigationCurrentlyVisible = true,
                isNavigationTargetVisible = false,
            )
        )
        assertFalse(
            shouldCaptureNavigationBackdrop(
                mode = AppNavigationMode.BottomOverlay,
                supportsBackdropEffects = true,
                isNavigationCurrentlyVisible = false,
                isNavigationTargetVisible = false,
            )
        )
        assertFalse(
            shouldCaptureNavigationBackdrop(
                mode = AppNavigationMode.SideRail,
                supportsBackdropEffects = true,
                isNavigationCurrentlyVisible = true,
                isNavigationTargetVisible = true,
            )
        )
    }

    @Test
    fun `bottom navigation keeps its clearance through the complete exit transition`() {
        assertTrue(
            shouldReserveBottomNavigationSpace(
                mode = AppNavigationMode.BottomOverlay,
                isNavigationCurrentlyVisible = true,
                isNavigationTargetVisible = false,
            )
        )
        assertTrue(
            shouldReserveBottomNavigationSpace(
                mode = AppNavigationMode.BottomOverlay,
                isNavigationCurrentlyVisible = false,
                isNavigationTargetVisible = true,
            )
        )
        assertFalse(
            shouldReserveBottomNavigationSpace(
                mode = AppNavigationMode.BottomOverlay,
                isNavigationCurrentlyVisible = false,
                isNavigationTargetVisible = false,
            )
        )
        assertFalse(
            shouldReserveBottomNavigationSpace(
                mode = AppNavigationMode.SideRail,
                isNavigationCurrentlyVisible = true,
                isNavigationTargetVisible = true,
            )
        )
    }

    @Test
    fun `floating navigation width follows its item count instead of filling the phone`() {
        assertEquals(
            236.dp,
            calculateFloatingNavigationWidth(
                containerWidth = 432.dp,
                itemCount = 3,
            )
        )
        assertEquals(
            200.dp,
            calculateFloatingNavigationWidth(
                containerWidth = 240.dp,
                itemCount = 3,
            )
        )
    }

    @Test
    fun `compact top-level content clears the measured navigation and both 12 dp gaps`() {
        assertEquals(
            120.dp,
            calculateContentBottomPadding(
                mode = AppNavigationMode.BottomOverlay,
                isTopLevelRoute = true,
                safeBottomInset = 24.dp,
                measuredNavigationHeight = 72.dp,
            )
        )
    }

    @Test
    fun `compact top-level content follows the measured navigation height`() {
        val shorterNavigation = calculateContentBottomPadding(
            mode = AppNavigationMode.BottomOverlay,
            isTopLevelRoute = true,
            safeBottomInset = 8.dp,
            measuredNavigationHeight = 64.dp,
        )
        val tallerNavigation = calculateContentBottomPadding(
            mode = AppNavigationMode.BottomOverlay,
            isTopLevelRoute = true,
            safeBottomInset = 8.dp,
            measuredNavigationHeight = 88.dp,
        )

        assertEquals(96.dp, shorterNavigation)
        assertEquals(120.dp, tallerNavigation)
    }

    @Test
    fun `compact detail content keeps regular spacing above the safe bottom inset`() {
        assertEquals(
            40.dp,
            calculateContentBottomPadding(
                mode = AppNavigationMode.BottomOverlay,
                isTopLevelRoute = false,
                safeBottomInset = 24.dp,
                measuredNavigationHeight = 80.dp,
            )
        )
    }

    @Test
    fun `rail content clears a visible floating utility`() {
        assertEquals(
            24.dp,
            calculateContentBottomPadding(
                mode = AppNavigationMode.SideRail,
                isTopLevelRoute = true,
                safeBottomInset = 24.dp,
                measuredNavigationHeight = 80.dp,
            )
        )
        assertEquals(
            108.dp,
            calculateContentBottomPadding(
                mode = AppNavigationMode.SideRail,
                isTopLevelRoute = true,
                safeBottomInset = 24.dp,
                measuredNavigationHeight = 80.dp,
                floatingUtilityHeight = 56.dp,
            )
        )
    }

    @Test
    fun `compact layers use both safe edges while rail content uses only the trailing edge`() {
        val compact = calculateLayoutPadding(
            mode = AppNavigationMode.BottomOverlay,
            safeStartInset = 20.dp,
            safeEndInset = 12.dp,
        )
        val rail = calculateLayoutPadding(
            mode = AppNavigationMode.SideRail,
            safeStartInset = 20.dp,
            safeEndInset = 12.dp,
        )

        assertEquals(20.dp, compact.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, compact.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, rail.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, rail.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun `remote control fab adds enough clearance for the final content item`() {
        assertEquals(
            188.dp,
            calculateContentBottomPadding(
                mode = AppNavigationMode.BottomOverlay,
                isTopLevelRoute = true,
                safeBottomInset = 24.dp,
                measuredNavigationHeight = 72.dp,
                floatingUtilityHeight = 56.dp,
            )
        )
    }
}
