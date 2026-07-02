package com.m3u.data.repository.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCategoryOrderTest {
    @Test
    fun orderPlaylistCategoriesUsesCustomOrderBeforePinnedCategories() {
        val categories = listOf("News", "Sports", "Kids", "Movies")

        assertEquals(
            listOf("Kids", "News", "Movies", "Sports"),
            categories.orderPlaylistCategories(
                orderedCategories = listOf("Kids", "News"),
                pinnedCategories = listOf("Movies"),
                hiddenCategories = emptyList()
            )
        )
    }

    @Test
    fun orderPlaylistCategoriesKeepsPinnedOrderWhenCustomOrderIsEmpty() {
        val categories = listOf("News", "Sports", "Kids", "Movies")

        assertEquals(
            listOf("Kids", "Movies", "News", "Sports"),
            categories.orderPlaylistCategories(
                orderedCategories = emptyList(),
                pinnedCategories = listOf("Kids", "Movies"),
                hiddenCategories = emptyList()
            )
        )
    }

    @Test
    fun orderPlaylistCategoriesFiltersHiddenCategories() {
        val categories = listOf("News", "Sports", "Kids", "Movies")

        assertEquals(
            listOf("Movies", "News", "Kids"),
            categories.orderPlaylistCategories(
                orderedCategories = listOf("Movies", "Sports", "News"),
                pinnedCategories = emptyList(),
                hiddenCategories = listOf("Sports")
            )
        )
    }
}
