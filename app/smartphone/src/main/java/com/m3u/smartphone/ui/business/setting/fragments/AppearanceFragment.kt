package com.m3u.smartphone.ui.business.setting.fragments

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.preferences.ClipMode
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.mutablePreferenceOf
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.ColorScheme
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.setting.components.SwitchSharedPreference
import com.m3u.smartphone.ui.material.components.MessageItem
import com.m3u.smartphone.ui.material.components.Preference
import com.m3u.smartphone.ui.material.components.TextPreference
import com.m3u.smartphone.ui.material.components.ThemeSelection
import com.m3u.smartphone.ui.material.ktx.Edge
import com.m3u.smartphone.ui.material.ktx.blurEdges
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalSpacing

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

    var isDarkMode by mutablePreferenceOf(PreferencesKeys.DARK_MODE)
    var useDynamicColors by mutablePreferenceOf(PreferencesKeys.USE_DYNAMIC_COLORS)
    var argb by mutablePreferenceOf(PreferencesKeys.COLOR_ARGB)
    var clipMode by mutablePreferenceOf(PreferencesKeys.CLIP_MODE)
    var compactDimension by mutablePreferenceOf(PreferencesKeys.COMPACT_DIMENSION)
    var noPictureMode by mutablePreferenceOf(PreferencesKeys.NO_PICTURE_MODE)
    var followSystemTheme by mutablePreferenceOf(PreferencesKeys.FOLLOW_SYSTEM_THEME)
    var colorfulBackground by mutablePreferenceOf(PreferencesKeys.COLORFUL_BACKGROUND)
    var godMode by mutablePreferenceOf(PreferencesKeys.GOD_MODE)

    val colorScheme = MaterialTheme.colorScheme

    val leftContentDescription = stringResource(string.ui_theme_card_left)
    val rightContentDescription = stringResource(string.ui_theme_card_right)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(
                    containerColor = colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .background(colorScheme.surfaceContainerHighest)
                        .padding(spacing.medium)
                        .fillMaxWidth(),
                ) {
                    @SuppressLint("UnusedBoxWithConstraintsScope")
                    BoxWithConstraints(
                        Modifier.align(Alignment.Start)
                    ) {
                        MessageItem(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                            left = true,
                            contentDescription = leftContentDescription,
                            modifier = Modifier.sizeIn(maxWidth = maxWidth * 0.8f)
                        )
                    }
                    Spacer(Modifier.height(spacing.small))
                    @SuppressLint("UnusedBoxWithConstraintsScope")
                    BoxWithConstraints(
                        Modifier.align(Alignment.End)
                    ) {
                        MessageItem(
                            containerColor = colorScheme.secondary,
                            contentColor = colorScheme.onSecondary,
                            left = false,
                            contentDescription = rightContentDescription,
                            modifier = Modifier.sizeIn(maxWidth = maxWidth * 0.8f)
                        )
                    }

                }
                HorizontalDivider()
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .blurEdges(
                            colorScheme.surface, edges = listOf(Edge.Start, Edge.End)
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    contentPadding = PaddingValues(spacing.medium)
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
                                useDynamicColors = false
                                argb = colorScheme.argb
                                isDarkMode = colorScheme.isDark
                            },
                            onLongClick = { openColorCanvas(colorScheme) },
                        )
                    }
//                        item {
//                            val inDarkTheme = isSystemInDarkTheme()
//                            ThemeAddSelection {
//                                openColorCanvas(
//                                    ColorScheme(
//                                        argb = Color(
//                                            red = (0..0xFF).random(),
//                                            green = (0..0xFF).random(),
//                                            blue = (0..0xFF).random()
//                                        ).toArgb(),
//                                        isDark = inDarkTheme,
//                                        name = ColorScheme.NAME_TEMP
//                                    )
//                                )
//                            }
//                        }
                }
            }

        }
        item {
            TextPreference(
                title = stringResource(string.feat_setting_clip_mode).title(),
                icon = Icons.Rounded.FitScreen,
                trailing = when (clipMode) {
                    ClipMode.ADAPTIVE -> stringResource(string.feat_setting_clip_mode_adaptive)
                    ClipMode.CLIP -> stringResource(string.feat_setting_clip_mode_clip)
                    ClipMode.STRETCHED -> stringResource(string.feat_setting_clip_mode_stretched)
                    else -> ""
                }.title(),
                onClick = {
                    clipMode = when (clipMode) {
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
                checked = compactDimension,
                onChanged = { compactDimension = !compactDimension }
            )
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_no_picture_mode,
                content = string.feat_setting_no_picture_mode_description,
                icon = Icons.Rounded.HideImage,
                checked = noPictureMode,
                onChanged = { noPictureMode = !noPictureMode }
            )
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_follow_system_theme,
                icon = Icons.Rounded.DarkMode,
                checked = followSystemTheme,
                onChanged = { followSystemTheme = !followSystemTheme },
            )
        }
        item {
            val useDynamicColorsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            SwitchSharedPreference(
                title = string.feat_setting_use_dynamic_colors,
                content = string.feat_setting_use_dynamic_colors_unavailable.takeUnless { useDynamicColorsAvailable },
                icon = Icons.Rounded.ColorLens,
                checked = useDynamicColors,
                onChanged = { useDynamicColors = !useDynamicColors },
                enabled = useDynamicColorsAvailable
            )
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_colorful_background,
                icon = Icons.Rounded.Stars,
                checked = colorfulBackground,
                onChanged = {
                    colorfulBackground = !colorfulBackground
                }
            )
        }
        item {
            Preference(
                title = stringResource(string.feat_setting_restore_schemes).title(),
                icon = Icons.Rounded.Restore,
                onClick = restoreSchemes
            )
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_god_mode,
                content = string.feat_setting_god_mode_description,
                icon = Icons.Rounded.DeviceHub,
                checked = godMode,
                onChanged = { godMode = !godMode }
            )
        }
    }
}
