package com.m3u.tv

import java.text.Collator
import java.util.Locale

/**
 * Builds a case-insensitive, locale-aware comparator for user-visible text.
 *
 * Kotlin's [sortedWith] is stable, so items whose primary and secondary labels
 * collate equally retain their repository order.
 */
internal fun <T> localeAwareComparator(
    primarySelector: (T) -> String,
    secondarySelector: ((T) -> String)? = null,
    locale: Locale = Locale.getDefault(),
): Comparator<T> {
    val collator = Collator.getInstance(locale).apply {
        strength = Collator.SECONDARY
        decomposition = Collator.CANONICAL_DECOMPOSITION
    }
    return Comparator { left, right ->
        val primaryComparison = collator.compare(
            primarySelector(left),
            primarySelector(right),
        )
        if (primaryComparison != 0 || secondarySelector == null) {
            primaryComparison
        } else {
            collator.compare(
                secondarySelector(left),
                secondarySelector(right),
            )
        }
    }
}
