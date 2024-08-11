package com.m3u.feature.setting.fragments

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.preferences.ClipMode
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.ColorScheme
import com.m3u.feature.setting.components.SwitchSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference
import com.m3u.material.components.TextPreference
import com.m3u.material.components.ThemeAddSelection
import com.m3u.material.components.ThemeSelection
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.minus
import com.m3u.material.ktx.only
import com.m3u.material.ktx.plus
import com.m3u.material.ktx.textHorizontalLabel
import com.m3u.material.model.LocalSpacing

@Composable
internal fun AppearanceFragment(
    colorSchemes: List<ColorScheme>,
    colorArgb: Int,
    openColorCanvas: (ColorScheme) -> Unit,
    restoreSchemes: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()

    val isDarkMode = preferences.darkMode
    val useDynamicColors = preferences.useDynamicColors
    val tv = tv()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding - contentPadding.only(WindowInsetsSides.Bottom))
    ) {
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
            items(
                items = colorSchemes,
                key = { "${it.argb}_${it.isDark}" }
            ) { colorScheme ->
                val selected =
                    !useDynamicColors && colorArgb == colorScheme.argb && isDarkMode == colorScheme.isDark
                ThemeSelection(
                    argb = colorScheme.argb,
                    isDark = colorScheme.isDark,
                    selected = selected,
                    onClick = {
                        preferences.useDynamicColors = false
                        preferences.argb = colorScheme.argb
                        preferences.darkMode = colorScheme.isDark
                    },
                    onLongClick = { openColorCanvas(colorScheme) },
                    name = colorScheme.name,
                    leftContentDescription = stringResource(string.ui_theme_card_left),
                    rightContentDescription = stringResource(string.ui_theme_card_right)
                )
            }
            item {
                val inDarkTheme = isSystemInDarkTheme()
                ThemeAddSelection {
                    openColorCanvas(
                        ColorScheme(
                            argb = Color(
                                red = (0..0xFF).random(),
                                green = (0..0xFF).random(),
                                blue = (0..0xFF).random()
                            ).toArgb(),
                            isDark = inDarkTheme,
                            name = ColorScheme.NAME_TEMP
                        )
                    )
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            contentPadding = contentPadding - contentPadding.only(WindowInsetsSides.Top) +
                    PaddingValues(horizontal = spacing.medium),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                TextPreference(
                    title = stringResource(string.feat_setting_clip_mode).title(),
                    icon = Icons.Rounded.FitScreen,
                    trailing = when (preferences.clipMode) {
                        ClipMode.ADAPTIVE -> stringResource(string.feat_setting_clip_mode_adaptive)
                        ClipMode.CLIP -> stringResource(string.feat_setting_clip_mode_clip)
                        ClipMode.STRETCHED -> stringResource(string.feat_setting_clip_mode_stretched)
                        else -> ""
                    }.title(),
                    onClick = {
                        preferences.clipMode = when (preferences.clipMode) {
                            ClipMode.ADAPTIVE -> ClipMode.CLIP
                            ClipMode.CLIP -> ClipMode.STRETCHED
                            ClipMode.STRETCHED -> ClipMode.ADAPTIVE
                            else -> ClipMode.ADAPTIVE
                        }
                    }
                )
            }
            item {
                SwitchSharedPreference(
                    title = string.feat_setting_compact_dimension,
                    icon = Icons.Rounded.FormatSize,
                    checked = preferences.compactDimension,
                    onChanged = { preferences.compactDimension = !preferences.compactDimension }
                )
            }
            item {
                SwitchSharedPreference(
                    title = string.feat_setting_no_picture_mode,
                    content = string.feat_setting_no_picture_mode_description,
                    icon = Icons.Rounded.HideImage,
                    checked = preferences.noPictureMode,
                    onChanged = { preferences.noPictureMode = !preferences.noPictureMode }
                )
            }
            item {
                SwitchSharedPreference(
                    title = string.feat_setting_follow_system_theme,
                    icon = Icons.Rounded.DarkMode,
                    checked = preferences.followSystemTheme,
                    onChanged = { preferences.followSystemTheme = !preferences.followSystemTheme },
                )
            }
            item {
                val useDynamicColorsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                SwitchSharedPreference(
                    title = string.feat_setting_use_dynamic_colors,
                    content = string.feat_setting_use_dynamic_colors_unavailable.takeUnless { useDynamicColorsAvailable },
                    icon = Icons.Rounded.ColorLens,
                    checked = useDynamicColors,
                    onChanged = { preferences.useDynamicColors = !useDynamicColors },
                    enabled = useDynamicColorsAvailable
                )
            }
            if (!tv) {
                item {
                    SwitchSharedPreference(
                        title = string.feat_setting_colorful_background,
                        icon = Icons.Rounded.Stars,
                        checked = preferences.colorfulBackground,
                        onChanged = {
                            preferences.colorfulBackground = !preferences.colorfulBackground
                        }
                    )
                }
            }
            item {
                Preference(
                    title = stringResource(string.feat_setting_restore_schemes).title(),
                    icon = Icons.Rounded.Restore,
                    onClick = restoreSchemes
                )
            }
            item {
                if (!tv) {
                    SwitchSharedPreference(
                        title = string.feat_setting_god_mode,
                        content = string.feat_setting_god_mode_description,
                        icon = Icons.Rounded.DeviceHub,
                        checked = preferences.godMode,
                        onChanged = { preferences.godMode = !preferences.godMode }
                    )
                }
            }
        }
    }
}
