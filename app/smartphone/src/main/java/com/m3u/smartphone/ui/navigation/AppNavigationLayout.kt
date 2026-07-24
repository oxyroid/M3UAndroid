package com.m3u.smartphone.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class AppNavigationMode {
    BottomOverlay,
    SideRail,
}

@Immutable
internal data class AppContentInsets(
    val layoutPadding: PaddingValues,
    val contentPadding: PaddingValues,
    val navigationClearance: Dp,
)

internal fun resolveAppNavigationMode(windowWidth: Dp): AppNavigationMode =
    if (windowWidth < 600.dp) {
        AppNavigationMode.BottomOverlay
    } else {
        AppNavigationMode.SideRail
    }

internal fun shouldShowBottomNavigation(
    mode: AppNavigationMode,
    isTopLevelRoute: Boolean,
    isSearchActive: Boolean,
    isImeVisible: Boolean,
): Boolean = mode == AppNavigationMode.BottomOverlay &&
    isTopLevelRoute &&
    !isSearchActive &&
    !isImeVisible

internal fun shouldReserveBottomNavigationSpace(
    mode: AppNavigationMode,
    isNavigationCurrentlyVisible: Boolean,
    isNavigationTargetVisible: Boolean,
): Boolean = mode == AppNavigationMode.BottomOverlay &&
    (isNavigationCurrentlyVisible || isNavigationTargetVisible)

internal fun shouldCaptureNavigationBackdrop(
    mode: AppNavigationMode,
    supportsBackdropEffects: Boolean,
    isNavigationCurrentlyVisible: Boolean,
    isNavigationTargetVisible: Boolean,
): Boolean = supportsBackdropEffects &&
    shouldReserveBottomNavigationSpace(
        mode = mode,
        isNavigationCurrentlyVisible = isNavigationCurrentlyVisible,
        isNavigationTargetVisible = isNavigationTargetVisible,
    )

internal fun shouldShowBottomEdgeBlur(mode: AppNavigationMode): Boolean =
    mode == AppNavigationMode.SideRail

internal fun calculateFloatingNavigationWidth(
    containerWidth: Dp,
    itemCount: Int,
    maximumWidth: Dp = 360.dp,
): Dp {
    val safeItemCount = itemCount.coerceAtLeast(1)
    val innerHorizontalPadding = 8.dp
    val preferredWidth = (76.dp * safeItemCount) + innerHorizontalPadding
    val minimumWidth = (52.dp * safeItemCount) + innerHorizontalPadding
    val widthWithinEdges = (containerWidth - 20.dp * 2)
        .coerceAtLeast(minimumWidth)

    return minOf(
        preferredWidth,
        widthWithinEdges,
        maximumWidth,
        containerWidth,
    )
}

internal fun calculateLayoutPadding(
    mode: AppNavigationMode,
    safeStartInset: Dp,
    safeEndInset: Dp,
): PaddingValues = when (mode) {
    AppNavigationMode.BottomOverlay -> PaddingValues(
        start = safeStartInset,
        end = safeEndInset,
    )

    AppNavigationMode.SideRail -> PaddingValues(end = safeEndInset)
}

internal fun calculateContentBottomPadding(
    mode: AppNavigationMode,
    isTopLevelRoute: Boolean,
    safeBottomInset: Dp,
    measuredNavigationHeight: Dp,
    floatingUtilityHeight: Dp = 0.dp,
    navigationBottomGap: Dp = 12.dp,
    contentGap: Dp = 12.dp,
    regularContentSpacing: Dp = 16.dp,
): Dp {
    if (mode == AppNavigationMode.SideRail) {
        return if (floatingUtilityHeight > 0.dp) {
            safeBottomInset + regularContentSpacing + floatingUtilityHeight + contentGap
        } else {
            safeBottomInset
        }
    }

    val navigationPadding = when {
        isTopLevelRoute -> {
            safeBottomInset + navigationBottomGap + measuredNavigationHeight + contentGap
        }

        else -> {
            safeBottomInset + regularContentSpacing
        }
    }
    if (floatingUtilityHeight <= 0.dp) return navigationPadding

    val utilityBottomPadding = if (isTopLevelRoute) {
        navigationPadding
    } else {
        safeBottomInset + regularContentSpacing
    }
    return maxOf(
        navigationPadding,
        utilityBottomPadding + floatingUtilityHeight + contentGap,
    )
}
