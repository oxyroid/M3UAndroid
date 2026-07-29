package com.m3u.core.foundation.architecture.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferencesTest {
    @Test
    fun `applying a theme emits only the complete final snapshot`() = withStore { store ->
        val initial = ThemePreferencesSnapshot(
            selection = ThemePreference(
                presetId = ThemePreset.MATERIAL,
                argb = 0x123456,
                isDark = true,
                style = ThemeStyle.MATERIAL,
            ),
            useDynamicColors = true,
            followSystemTheme = true,
        )
        store.edit { preferences ->
            preferences[PreferencesKeys.COLOR_ARGB] = initial.selection.argb
            preferences[PreferencesKeys.DARK_MODE] = initial.selection.isDark
            preferences[PreferencesKeys.THEME_STYLE] = initial.selection.style
            preferences[PreferencesKeys.THEME_PRESET_ID] =
                initial.selection.presetId
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] =
                initial.useDynamicColors
            preferences[PreferencesKeys.FOLLOW_SYSTEM_THEME] =
                initial.followSystemTheme
        }

        val snapshots = Channel<ThemePreferencesSnapshot>(capacity = 2)
        val collection = launch(Dispatchers.Default) {
            store.themePreferences().take(2).collect(snapshots::send)
        }
        assertEquals(initial, withTimeout(TEST_TIMEOUT_MILLIS) { snapshots.receive() })

        store.applyThemePreference(
            ThemePreference(
                presetId = ThemePreset.WARM_EDITORIAL,
                argb = 0,
                isDark = false,
                style = ThemeStyle.MATERIAL,
            )
        )

        assertEquals(
            ThemePreferencesSnapshot(
                selection = ThemePreference(
                    presetId = ThemePreset.WARM_EDITORIAL,
                    argb = ThemePreset.WARM_EDITORIAL_SEED,
                    isDark = false,
                    style = ThemeStyle.WARM_EDITORIAL,
                ),
                useDynamicColors = false,
                followSystemTheme = true,
            ),
            withTimeout(TEST_TIMEOUT_MILLIS) { snapshots.receive() },
        )
        collection.join()
    }

    @Test
    fun `legacy warm style without preset resolves to warm editorial`() =
        withStore { store ->
            store.edit { preferences ->
                preferences[PreferencesKeys.COLOR_ARGB] =
                    ThemePreset.WARM_EDITORIAL_SEED
                preferences[PreferencesKeys.DARK_MODE] = true
                preferences[PreferencesKeys.THEME_STYLE] =
                    ThemeStyle.WARM_EDITORIAL
            }

            assertEquals(
                ThemePreference(
                    presetId = ThemePreset.WARM_EDITORIAL,
                    argb = ThemePreset.WARM_EDITORIAL_SEED,
                    isDark = true,
                    style = ThemeStyle.WARM_EDITORIAL,
                ),
                store.themePreferences().first().selection,
            )
        }

    @Test
    fun `unknown preset safely resolves to material without losing seed`() =
        withStore { store ->
            store.edit { preferences ->
                preferences[PreferencesKeys.COLOR_ARGB] = 0x654321
                preferences[PreferencesKeys.DARK_MODE] = false
                preferences[PreferencesKeys.THEME_STYLE] =
                    ThemeStyle.WARM_EDITORIAL
                preferences[PreferencesKeys.THEME_PRESET_ID] = "future-theme"
            }

            assertEquals(
                ThemePreference(
                    presetId = ThemePreset.MATERIAL,
                    argb = 0x654321,
                    isDark = false,
                    style = ThemeStyle.MATERIAL,
                ),
                store.themePreferences().first().selection,
            )
        }

    private fun withStore(
        block: suspend kotlinx.coroutines.CoroutineScope.(Settings) -> Unit,
    ) = runBlocking {
        val directory = Files.createTempDirectory("m3u-theme-preferences").toFile()
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = {
                File(directory, "settings.preferences_pb")
            },
        )
        try {
            block(store)
        } finally {
            storeScope.cancel()
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }
}
