package com.m3u.feature.setting.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m3u.data.database.model.ColorScheme
import com.m3u.i18n.R.string
import com.m3u.material.components.Icon
import com.m3u.material.ktx.createScheme
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.SugarColors
import com.m3u.ui.FontFamilies

@OptIn(ExperimentalStdlibApi::class)
@Composable
internal fun CanvasBottomSheet(
    sheetState: SheetState,
    colorScheme: ColorScheme?,
    onApplyColor: (Int, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val argb = colorScheme?.argb
    val isDark = colorScheme?.isDark
    val isTemp = colorScheme?.name == ColorScheme.NAME_TEMP
    if (argb != null && isDark != null) {
        var currentColorInt: Int by remember(argb) { mutableIntStateOf(argb) }
        var currentIsDark: Boolean by remember(isDark) { mutableStateOf(isDark) }

        val scheme by remember {
            derivedStateOf {
                createScheme(currentColorInt, currentIsDark)
            }
        }

        MaterialTheme(
            colorScheme = scheme
        ) {
            ModalBottomSheet(
                sheetState = sheetState,
                onDismissRequest = onDismissRequest,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = spacing.medium)
                        .then(modifier)
                ) {

                    val color by remember { derivedStateOf { Color(currentColorInt).copy(alpha = 1f) } }

                    val red by remember { derivedStateOf { color.red } }
                    val green by remember { derivedStateOf { color.green } }
                    val blue by remember { derivedStateOf { color.blue } }

                    val colorText by remember {
                        derivedStateOf { "#${color.toArgb().toHexString(HexFormat.UpperCase)}" }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = color,
                        )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text(
                                text = colorText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamilies.JetbrainsMono,
                            )
                        }
                    }

                    Slider(
                        value = red,
                        onValueChange = { currentColorInt = color.copy(red = it).toArgb() }
                    )
                    Slider(
                        value = green,
                        onValueChange = { currentColorInt = color.copy(green = it).toArgb() }
                    )
                    Slider(
                        value = blue,
                        onValueChange = { currentColorInt = color.copy(blue = it).toArgb() }
                    )

                    val hasChanged by remember {
                        derivedStateOf { isTemp || currentColorInt != argb || currentIsDark != isDark }
                    }
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = false,
                            enabled = hasChanged,
                            onClick = {
                                onApplyColor(currentColorInt, currentIsDark)
                                onDismissRequest()
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                baseShape = RoundedCornerShape(8.dp),
                                index = 0,
                                count = 3
                            ),
                            colors = SegmentedButtonDefaults.colors(
                                disabledInactiveContentColor = LocalContentColor.current.copy(0.38f)
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = stringResource(string.feat_setting_canvas_apply)
                                )
                            },
                            label = {
                                Text(stringResource(string.feat_setting_canvas_apply).uppercase())
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedButton(
                            selected = false,
                            enabled = hasChanged,
                            colors = SegmentedButtonDefaults.colors(
                                disabledInactiveContentColor = LocalContentColor.current.copy(0.38f)
                            ),
                            onClick = {
                                currentColorInt = argb
                                currentIsDark = isDark
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                baseShape = RoundedCornerShape(8.dp),
                                index = 1,
                                count = 3
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(string.feat_setting_canvas_reset)
                                )
                            },
                            label = {
                                Text(stringResource(string.feat_setting_canvas_reset).uppercase())
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedButton(
                            selected = false,
                            onClick = { currentIsDark = !currentIsDark },
                            shape = SegmentedButtonDefaults.itemShape(
                                baseShape = RoundedCornerShape(8.dp),
                                index = 2,
                                count = 3
                            ),
                            icon = {
                                Crossfade(currentIsDark, label = "icon") { currentIsDark ->
                                    Icon(
                                        imageVector = when (currentIsDark) {
                                            true -> Icons.Rounded.DarkMode
                                            false -> Icons.Rounded.LightMode
                                        },
                                        contentDescription = "",
                                        tint = when (currentIsDark) {
                                            true -> SugarColors.Tee
                                            false -> SugarColors.Yellow
                                        }
                                    )
                                }
                            },
                            label = {
                                Crossfade(currentIsDark, label = "label") { currentIsDark ->
                                    Text(
                                        text = stringResource(
                                            when (currentIsDark) {
                                                true -> string.feat_setting_canvas_dark
                                                false -> string.feat_setting_canvas_light
                                            }
                                        ).uppercase()
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

            }
        }
    }
}