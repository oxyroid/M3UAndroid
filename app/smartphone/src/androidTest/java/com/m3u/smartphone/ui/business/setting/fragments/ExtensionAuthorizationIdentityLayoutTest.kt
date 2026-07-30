package com.m3u.smartphone.ui.business.setting.fragments

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.m3u.smartphone.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExtensionAuthorizationIdentityLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullTechnicalIdentityWrapsWithoutEllipsisAtTwoHundredPercentText() {
        val packageName =
            "dev.example.extension.with.a.deliberately.long.application.identifier"
        val certificate = CERTIFICATE_SHA256.chunked(16).joinToString(" ")

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = baseDensity.density,
                            fontScale = 2f,
                        ),
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                    ) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .width(320.dp)
                                    .padding(16.dp),
                            ) {
                                ExtensionAuthorizationIdentitySummaryLine(
                                    label = "Package",
                                    value = packageName,
                                    testTag = "identity-package",
                                )
                                ExtensionAuthorizationIdentitySummaryLine(
                                    label = "Certificate SHA-256",
                                    value = certificate,
                                    testTag = "identity-certificate",
                                )
                            }
                        }
                    }
                }
            }
        }

        assertFullTechnicalValueIsLaidOut(packageName)
        assertFullTechnicalValueIsLaidOut(certificate)
    }

    private fun assertFullTechnicalValueIsLaidOut(value: String) {
        val textNode = composeRule.onNodeWithText(
            text = value,
            substring = false,
            ignoreCase = false,
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = requireNotNull(
            textNode.config[SemanticsActions.GetTextLayoutResult].action
        )
        assertTrue(action.invoke(layouts))
        val layout = layouts.single { result ->
            result.layoutInput.text.text == value
        }

        assertEquals(2f, layout.layoutInput.density.fontScale, 0f)
        assertEquals(TextDirection.Ltr, layout.layoutInput.style.textDirection)
        assertEquals(FontFamily.Monospace, layout.layoutInput.style.fontFamily)
        assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines)
        assertEquals(TextOverflow.Clip, layout.layoutInput.overflow)
        assertTrue(layout.layoutInput.softWrap)
        assertTrue("Fixture must wrap onto multiple lines", layout.lineCount > 1)
        repeat(layout.lineCount) { line ->
            assertFalse(
                "Technical identity line $line was unexpectedly ellipsized",
                layout.isLineEllipsized(line),
            )
        }
        assertEquals(
            "The complete technical identity was not laid out",
            value.length,
            layout.getLineEnd(layout.lineCount - 1),
        )
    }

    private companion object {
        const val CERTIFICATE_SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
