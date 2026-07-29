package com.m3u.tv

import com.m3u.data.database.model.DataSource
import kotlin.math.roundToInt

internal fun tvSupportsPlaylistSource(source: DataSource): Boolean = when (source) {
    DataSource.M3U,
    DataSource.Xtream -> true

    else -> false
}

internal enum class TvHeroAction {
    PRIMARY,
    SECONDARY,
}

internal enum class TvHorizontalDirection {
    LEFT,
    RIGHT,
}

internal data class TvLargeTextLayout(
    val heroMinHeightDp: Int,
    val heroTextWidthFraction: Float,
    val playlistCardMinHeightDp: Int,
    val metricTileMinHeightDp: Int,
    val stackEmptyLibrary: Boolean,
    val emptySetupMinHeightDp: Int,
)

/**
 * Keeps the first TV viewport compact at the default scale while allowing
 * text containers to grow instead of clipping accessibility-sized text.
 */
internal fun tvLargeTextLayout(fontScale: Float): TvLargeTextLayout {
    val normalizedScale = fontScale.coerceIn(1f, 3f)
    val scaleDelta = normalizedScale - 1f
    return TvLargeTextLayout(
        heroMinHeightDp = (288f + 240f * scaleDelta).roundToInt(),
        heroTextWidthFraction = (0.54f + 0.24f * scaleDelta).coerceAtMost(0.82f),
        playlistCardMinHeightDp = (144f + 72f * scaleDelta).roundToInt(),
        metricTileMinHeightDp = (136f + 72f * scaleDelta).roundToInt(),
        stackEmptyLibrary = normalizedScale >= 1.35f,
        emptySetupMinHeightDp = (356f + 64f * scaleDelta).roundToInt(),
    )
}

/**
 * Resolves a physical DPad move against the action order that is actually
 * displayed by a layout-direction-aware Row.
 *
 * A null result means the focused hero is already at that physical edge and
 * must let Compose continue focus search outside the hero.
 */
internal fun tvHeroActionAfterHorizontalMove(
    current: TvHeroAction,
    direction: TvHorizontalDirection,
    isRtl: Boolean,
): TvHeroAction? {
    val actionAtLeft = if (isRtl) TvHeroAction.SECONDARY else TvHeroAction.PRIMARY
    val actionAtRight = if (isRtl) TvHeroAction.PRIMARY else TvHeroAction.SECONDARY
    val destination = when (direction) {
        TvHorizontalDirection.LEFT -> actionAtLeft
        TvHorizontalDirection.RIGHT -> actionAtRight
    }
    return destination.takeUnless { it == current }
}

/**
 * Maps a logical leading-to-trailing gradient onto physical color stops.
 */
internal fun <T> tvLeadingGradientColorStops(
    isRtl: Boolean,
    leading: T,
    middle: T,
    trailing: T,
    middlePosition: Float,
): List<Pair<Float, T>> = if (isRtl) {
    listOf(
        0f to trailing,
        (1f - middlePosition) to middle,
        1f to leading,
    )
} else {
    listOf(
        0f to leading,
        middlePosition to middle,
        1f to trailing,
    )
}
