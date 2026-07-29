package com.m3u.data.extension

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionTextValidationTest {
    @Test
    fun lineBreakOptInAllowsOnlyLfControlCharacters() {
        assertFalse("first\nsecond".isSafeExtensionText(maximumLength = 32))
        assertTrue(
            "first\nsecond".isSafeExtensionText(
                maximumLength = 32,
                allowLineBreaks = true,
            )
        )
        assertFalse(
            "first\rsecond".isSafeExtensionText(
                maximumLength = 32,
                allowLineBreaks = true,
            )
        )
        assertFalse(
            "first\tsecond".isSafeExtensionText(
                maximumLength = 32,
                allowLineBreaks = true,
            )
        )
        assertFalse(
            "first\u2028second".isSafeExtensionText(
                maximumLength = 32,
                allowLineBreaks = true,
            )
        )
    }
}
