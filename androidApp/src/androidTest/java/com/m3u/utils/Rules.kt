package com.m3u.utils

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.rules.ActivityScenarioRule

fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.onNodeWithTextIgnoreCase(
    text: String
): SemanticsNodeInteraction {
    return onNodeWithText(text, ignoreCase = true)
}