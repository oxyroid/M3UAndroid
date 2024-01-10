package com.m3u.features.setting.fragments

import android.annotation.SuppressLint
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.google.android.material.color.utilities.Scheme
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R
import com.m3u.material.components.Background
import com.m3u.material.components.ThemeAddSelection
import com.m3u.material.components.ThemeSelection
import com.m3u.material.ktx.asColorScheme
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ColorPack(
    val argb: Int,
    val isDark: Boolean
)

@SuppressLint("RestrictedApi")
@Composable
internal fun ThemeFragment(
    packs: ImmutableList<ColorPack>,
    colorArgb: Int,
    onArgbMenu: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val pref = LocalPref.current
    val isDarkMode = pref.darkMode || isSystemInDarkTheme()
    val useDynamicColors = pref.useDynamicColors
    Background {
        Column(
            modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
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
                        leftContentDescription = "ho",
                        rightContentDescription = "la",
                        modifier = Modifier
                            .animateItemPlacement()
                    )
                }
                item(key = "add") {
                    ThemeAddSelection {

                    }
                }
            }

            val useDynamicColorsAvailable = Pref.DEFAULT_USE_DYNAMIC_COLORS

            CheckBoxSharedPreference(
                title = R.string.feat_setting_use_dynamic_colors,
                content = R.string
                    .feat_setting_use_dynamic_colors_unavailable.takeUnless { useDynamicColorsAvailable },
                checked = useDynamicColors,
                onChanged = { pref.useDynamicColors = !useDynamicColors },
                enabled = useDynamicColorsAvailable
            )

            CheckBoxSharedPreference(
                title = R.string.feat_setting_compact,
                checked = pref.compact,
                onChanged = { pref.compact = !pref.compact }
            )
        }
    }
}