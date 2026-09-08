@file:Suppress("unused")
package com.m3u.core.foundation.util.basic

import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import java.text.Normalizer
import java.util.Locale as JavaLocale

fun String.title(): String {
    if (this.isEmpty()) return this
    val split = this.split(" ")
    return split.joinToString(
        separator = " ",
        transform = { it.capitalize(Locale.current) }
    )
}

fun String.startsWithAny(vararg prefix: String, ignoreCase: Boolean = false): Boolean {
    return prefix.any { startsWith(it, ignoreCase) }
}

fun String.startWithHttpScheme(): Boolean = startsWithAny(
    "http://", "https://",
    ignoreCase = true
)

/**
 * Folds a string down to the form search compares against: no diacritics, no
 * case.
 *
 * SQLite's LIKE ignores case for ASCII but never accents, so "prenom" cannot
 * match "Le Prénom" however the query is written — and a catalogue in French
 * has a lot of those. Titles are stored pre-folded in a dedicated column and
 * the query is folded the same way, which keeps the comparison symmetric: with
 * or without accents, either side matches.
 *
 * NFD splits an accented letter into its base letter plus a combining mark, so
 * dropping the marks (Unicode category Mn) leaves the base letter behind.
 * Scripts without combining marks — CJK among them — pass through untouched.
 *
 * Locale.ROOT on purpose: a Turkish locale lowercases 'I' to a dotless 'ı',
 * which would quietly make titles unsearchable for those users.
 */
fun String.normalizeForSearch(): String = Normalizer
    .normalize(this, Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .lowercase(JavaLocale.ROOT)

private val COMBINING_MARKS = Regex("\\p{Mn}+")