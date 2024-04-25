package com.m3u.features.setting.fragments

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import com.m3u.core.architecture.preferences.LocalPreferences
import com.m3u.data.database.model.ColorPack
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.ThemeAddSelection
import com.m3u.material.components.ThemeSelection
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.textHorizontalLabel
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing

@Composable
internal fun AppearanceFragment(
    colorPacks: List<ColorPack>,
    colorArgb: Int,
    openColorCanvas: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val preferences = LocalPreferences.current

    val isDarkMode = preferences.darkMode
    val useDynamicColors = preferences.useDynamicColors
    val tv = isTelevision()

    Background {
        Column(
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .thenIf(tv) {
                    Modifier.padding(spacing.medium)
                }
        ) {
            if (!tv) {
                Text(
                    text = stringResource(string.feat_setting_appearance_hint_edit_color).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.textHorizontalLabel()
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(colorPacks, key = { "${it.argb}_${it.isDark}" }) { colorPack ->
                        val selected =
                            !useDynamicColors && colorArgb == colorPack.argb && isDarkMode == colorPack.isDark
                        ThemeSelection(
                            argb = colorPack.argb,
                            isDark = colorPack.isDark,
                            selected = selected,
                            onClick = {
                                preferences.useDynamicColors = false
                                preferences.argb = colorPack.argb
                                preferences.darkMode = colorPack.isDark
                            },
                            onLongClick = { openColorCanvas(colorPack.argb, colorPack.isDark) },
                            name = colorPack.name,
                            leftContentDescription = stringResource(string.ui_theme_card_left),
                            rightContentDescription = stringResource(string.ui_theme_card_right),
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                    item {
                        ThemeAddSelection {

                        }
                    }
                }
            } else {
                TvLazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(colorPacks, key = { "${it.argb}_${it.isDark}" }) { pack ->

                        val selected =
                            !useDynamicColors && colorArgb == pack.argb && isDarkMode == pack.isDark

                        ThemeSelection(
                            argb = pack.argb,
                            isDark = pack.isDark,
                            selected = selected,
                            onClick = {
                                preferences.useDynamicColors = false
                                preferences.argb = pack.argb
                                preferences.darkMode = pack.isDark
                            },
                            onLongClick = { },
                            name = pack.name,
                            leftContentDescription = stringResource(string.ui_theme_card_left),
                            rightContentDescription = stringResource(string.ui_theme_card_right),
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
            HorizontalDivider()

            CheckBoxSharedPreference(
                title = string.feat_setting_follow_system_theme,
                icon = Icons.Rounded.DarkMode,
                checked = preferences.followSystemTheme,
                onChanged = { preferences.followSystemTheme = !preferences.followSystemTheme },
            )

            val useDynamicColorsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            CheckBoxSharedPreference(
                title = string.feat_setting_use_dynamic_colors,
                content = string.feat_setting_use_dynamic_colors_unavailable.takeUnless { useDynamicColorsAvailable },
                icon = Icons.Rounded.ColorLens,
                checked = useDynamicColors,
                onChanged = { preferences.useDynamicColors = !useDynamicColors },
                enabled = useDynamicColorsAvailable
            )
        }
    }
}
