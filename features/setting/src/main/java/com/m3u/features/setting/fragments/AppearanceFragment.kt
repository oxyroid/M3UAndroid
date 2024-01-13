package com.m3u.features.setting.fragments

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import com.google.android.material.color.utilities.Scheme
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.material.components.Background
import com.m3u.material.components.ThemeAddSelection
import com.m3u.material.components.ThemeSelection
import com.m3u.material.ktx.asColorScheme
import com.m3u.i18n.R.string
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ColorPack(
    val argb: Int,
    val isDark: Boolean,
    val name: String
)

@SuppressLint("RestrictedApi")
@Composable
internal fun AppearanceFragment(
    packs: ImmutableList<ColorPack>,
    colorArgb: Int,
    onArgbMenu: (Int) -> Unit,
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
        ) {
            if (!tv) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(packs, key = { "${it.argb}_${it.isDark}" }) { pack ->
                        val colorScheme = remember(pack.argb, pack.isDark) {
                            when {
                                pack.isDark -> Scheme.dark(pack.argb)
                                else -> Scheme.light(pack.argb)
                            }.asColorScheme()
                        }
                        val selected =
                            !useDynamicColors && colorArgb == pack.argb && isDarkMode == pack.isDark

                        ThemeSelection(
                            colorScheme = colorScheme,
                            isDark = pack.isDark,
                            selected = selected,
                            onClick = {
                                pref.useDynamicColors = false
                                pref.colorArgb = pack.argb
                                pref.darkMode = pack.isDark
                            },
                            onLongClick = { onArgbMenu(pack.argb) },
                            name = pack.name,
                            leftContentDescription = stringResource(string.ui_theme_card_left),
                            rightContentDescription = stringResource(string.ui_theme_card_right),
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                    item(key = "add") {
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
                        val colorScheme = remember(pack.argb, pack.isDark) {
                            when {
                                pack.isDark -> Scheme.dark(pack.argb)
                                else -> Scheme.light(pack.argb)
                            }.asColorScheme()
                        }
                        val selected =
                            !useDynamicColors && colorArgb == pack.argb && isDarkMode == pack.isDark

                        ThemeSelection(
                            colorScheme = colorScheme,
                            isDark = pack.isDark,
                            selected = selected,
                            onClick = {
                                pref.useDynamicColors = false
                                pref.colorArgb = pack.argb
                                pref.darkMode = pack.isDark
                            },
                            onLongClick = { onArgbMenu(pack.argb) },
                            name = pack.name,
                            leftContentDescription = stringResource(string.ui_theme_card_left),
                            rightContentDescription = stringResource(string.ui_theme_card_right),
                            modifier = Modifier
                                .animateItemPlacement()
                        )
                    }
                    item(key = "add") {
                        ThemeAddSelection {

                        }
                    }
                }
            }

            val useDynamicColorsAvailable = Pref.DEFAULT_USE_DYNAMIC_COLORS

            CheckBoxSharedPreference(
                title = string.feat_setting_use_dynamic_colors,
                content = string.feat_setting_use_dynamic_colors_unavailable.takeUnless { useDynamicColorsAvailable },
                checked = useDynamicColors,
                onChanged = { pref.useDynamicColors = !useDynamicColors },
                enabled = useDynamicColorsAvailable
            )

            if (!tv) {
                CheckBoxSharedPreference(
                    title = string.feat_setting_compact,
                    checked = pref.compact,
                    onChanged = { pref.compact = !pref.compact }
                )
            }
        }
    }
}