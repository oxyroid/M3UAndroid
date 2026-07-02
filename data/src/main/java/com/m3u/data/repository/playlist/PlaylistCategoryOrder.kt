package com.m3u.data.repository.playlist

internal fun List<String>.orderPlaylistCategories(
    orderedCategories: List<String>,
    pinnedCategories: List<String>,
    hiddenCategories: List<String>
): List<String> {
    val orderedIndexes = orderedCategories
        .distinct()
        .withIndex()
        .associate { it.value to it.index }
    val pinnedIndexes = pinnedCategories
        .distinct()
        .withIndex()
        .associate { it.value to it.index }

    return filterNot { it in hiddenCategories }
        .sortedWith(
            compareBy<String> { orderedIndexes[it] ?: Int.MAX_VALUE }
                .thenBy { if (it in pinnedIndexes) 0 else 1 }
                .thenBy { pinnedIndexes[it] ?: Int.MAX_VALUE }
        )
}
