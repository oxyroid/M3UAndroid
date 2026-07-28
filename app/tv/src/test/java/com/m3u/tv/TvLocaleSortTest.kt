package com.m3u.tv

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class TvLocaleSortTest {
    @Test
    fun `uses locale collation for visible labels`() {
        val values = listOf("Örebro", "Zulu", "Ängel", "Åland")

        val sorted = values.sortedWith(
            localeAwareComparator(
                primarySelector = { it },
                locale = Locale.forLanguageTag("sv-SE"),
            )
        )

        assertEquals(listOf("Zulu", "Åland", "Ängel", "Örebro"), sorted)
    }

    @Test
    fun `uses title as stable secondary ordering`() {
        val values = listOf(
            TestLabel(category = "News", title = "Zulu", id = 1),
            TestLabel(category = "Sports", title = "Alpha", id = 2),
            TestLabel(category = "News", title = "alpha", id = 3),
            TestLabel(category = "NEWS", title = "ALPHA", id = 4),
        )

        val sorted = values.sortedWith(
            localeAwareComparator(
                primarySelector = TestLabel::category,
                secondarySelector = TestLabel::title,
                locale = Locale.ENGLISH,
            )
        )

        assertEquals(listOf(3, 4, 1, 2), sorted.map(TestLabel::id))
    }

    private data class TestLabel(
        val category: String,
        val title: String,
        val id: Int,
    )
}
