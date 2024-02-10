package com.m3u.features.setting.fragments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bento
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
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref
import com.m3u.data.database.model.ColorPack
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.ThemeAddSelection
import com.m3u.material.components.ThemeSelection
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun AppearanceFragment(
    packs: ImmutableList<ColorPack>,
    colorArgb: Int,
    openColorCanvas: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current
    val isDarkMode = pref.darkMode
    val useDynamicColors = pref.useDynamicColors
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
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(
                            horizontal = spacing.medium,
                            vertical = spacing.extraSmall
                        )
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(packs, key = { "${it.argb}_${it.isDark}" }) { pack ->
                        val selected =
                            !useDynamicColors && colorArgb == pack.argb && isDarkMode == pack.isDark
                        ThemeSelection(
                            argb = pack.argb,
                            isDark = pack.isDark,
                            selected = selected,
                            onClick = {
                                pref.useDynamicColors = false
                                pref.colorArgb = pack.argb
                                pref.darkMode = pack.isDark
                            },
                            onLongClick = { openColorCanvas(pack.argb, pack.isDark) },
                            name = pack.name,
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
                    items(packs, key = { "${it.argb}_${it.isDark}" }) { pack ->

                        val selected =
                            !useDynamicColors && colorArgb == pack.argb && isDarkMode == pack.isDark

                        ThemeSelection(
                            argb = pack.argb,
                            isDark = pack.isDark,
                            selected = selected,
                            onClick = {
                                pref.useDynamicColors = false
                                pref.colorArgb = pack.argb
                                pref.darkMode = pack.isDark
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
                checked = pref.followSystemTheme,
                onChanged = { pref.followSystemTheme = !pref.followSystemTheme },
            )

            val useDynamicColorsAvailable = Pref.DEFAULT_USE_DYNAMIC_COLORS

            CheckBoxSharedPreference(
                title = string.feat_setting_use_dynamic_colors,
                content = string.feat_setting_use_dynamic_colors_unavailable.takeUnless { useDynamicColorsAvailable },
                icon = Icons.Rounded.ColorLens,
                checked = useDynamicColors,
                onChanged = { pref.useDynamicColors = !useDynamicColors },
                enabled = useDynamicColorsAvailable
            )

            if (!tv) {
                CheckBoxSharedPreference(
                    title = string.feat_setting_compact,
                    icon = Icons.Rounded.Bento,
                    checked = pref.compact,
                    onChanged = { pref.compact = !pref.compact }
                )
            }
        }
    }
}
