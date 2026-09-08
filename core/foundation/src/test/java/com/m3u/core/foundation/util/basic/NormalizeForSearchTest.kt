package com.m3u.core.foundation.util.basic

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeForSearchTest {
    @Test
    fun `accents are folded away`() {
        assertEquals("le prenom", "Le Prénom".normalizeForSearch())
        assertEquals("amelie", "Amélie".normalizeForSearch())
        assertEquals("a bout de souffle", "À bout de souffle".normalizeForSearch())
        assertEquals("les miserables", "Les Misérables".normalizeForSearch())
    }

    @Test
    fun `a query typed with accents matches the same folded form`() {
        assertEquals("Le Prénom".normalizeForSearch(), "le prenom".normalizeForSearch())
        assertEquals("Le Prénom".normalizeForSearch(), "LE PRÉNOM".normalizeForSearch())
    }

    @Test
    fun `case is folded independently of the device locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            // Turkish lowercases 'I' to a dotless 'ı'; using the default locale
            // here would make "IT" unsearchable for those users.
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals("it crowd", "IT Crowd".normalizeForSearch())
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `scripts without combining marks are left alone`() {
        assertEquals("千と千尋の神隠し", "千と千尋の神隠し".normalizeForSearch())
        assertEquals("привет", "Привет".normalizeForSearch())
    }

    @Test
    fun `punctuation and spacing survive so substring matching still works`() {
        assertEquals("spider-man: no way home", "Spider-Man: No Way Home".normalizeForSearch())
        assertEquals("", "".normalizeForSearch())
    }
}
