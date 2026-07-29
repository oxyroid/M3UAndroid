package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.data.database.model.ColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceThemePolicyTest {
    @Test
    fun `follow system chooses the variant matching the current system theme`() {
        val light = ColorScheme(argb = 1, isDark = false, name = "light")
        val dark = ColorScheme(argb = 1, isDark = true, name = "dark")

        assertEquals(
            listOf(light),
            selectThemeSchemeVariants(
                schemes = listOf(light, dark),
                followSystemTheme = true,
                systemUsesDarkTheme = false,
            ),
        )
        assertEquals(
            listOf(dark),
            selectThemeSchemeVariants(
                schemes = listOf(light, dark),
                followSystemTheme = true,
                systemUsesDarkTheme = true,
            ),
        )
    }

    @Test
    fun `follow system keeps an unpaired custom theme available`() {
        val darkOnly = ColorScheme(
            argb = 2,
            isDark = true,
            name = "custom",
        )

        assertEquals(
            listOf(darkOnly),
            selectThemeSchemeVariants(
                schemes = listOf(darkOnly),
                followSystemTheme = true,
                systemUsesDarkTheme = false,
            ),
        )
    }

    @Test
    fun `manual mode keeps every explicit light and dark variant`() {
        val schemes = listOf(
            ColorScheme(argb = 1, isDark = false, name = "light"),
            ColorScheme(argb = 1, isDark = true, name = "dark"),
        )

        assertEquals(
            schemes,
            selectThemeSchemeVariants(
                schemes = schemes,
                followSystemTheme = false,
                systemUsesDarkTheme = false,
            ),
        )
    }

    @Test
    fun `dynamic colors are active only when requested and supported`() {
        assertFalse(areDynamicColorsActive(requested = false, supported = false))
        assertFalse(areDynamicColorsActive(requested = false, supported = true))
        assertFalse(areDynamicColorsActive(requested = true, supported = false))
        assertTrue(areDynamicColorsActive(requested = true, supported = true))
    }
}
