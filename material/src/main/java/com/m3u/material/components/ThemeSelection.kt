package com.m3u.material.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import com.m3u.material.LocalM3UHapticFeedback
import com.m3u.material.ktx.InteractionType
import com.m3u.material.ktx.createScheme
import com.m3u.material.ktx.interactionBorder
import com.m3u.material.ktx.tv
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.SugarColors
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Icon as TvIcon

@Composable
fun ThemeSelection(
    argb: Int,
    isDark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    name: String,
    leftContentDescription: String,
    rightContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val tv = tv()

    val colorScheme = remember(argb, isDark) {
        createScheme(argb, isDark)
    }

    val alpha by animateFloatAsState(
        targetValue = if (selected) 0f else 0.4f,
        label = "alpha"
    )
    val blurRadius by animateFloatAsState(
        targetValue = if (selected) 0f else 16f,
        label = "blur-radius"
    )
    val feedback = LocalM3UHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }

    val content: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier
                .graphicsLayer {
                    if (blurRadius != 0f) renderEffect = BlurEffect(blurRadius, blurRadius)
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.Black.copy(
                            alpha = alpha
                        )
                    )
                }
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(spacing.small)
            ) {
                ColorPiece(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    left = true,
                    contentDescription = leftContentDescription
                )
                ColorPiece(
                    containerColor = colorScheme.secondary,
                    contentColor = colorScheme.onSecondary,
                    left = false,
                    contentDescription = rightContentDescription
                )
            }
            Text(
                text = name.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.tertiaryContainer,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.onTertiaryContainer)
                    .basicMarquee(
                        iterations = if (selected) Int.MAX_VALUE else 0
                    )
                    .padding(2.dp)
            )
        }
    }
    Box(
        contentAlignment = Alignment.Center
    ) {
        if (!tv) {
            val zoom by animateFloatAsState(
                targetValue = if (selected) 0.95f else 0.85f,
                label = "zoom"
            )
            val corner by animateDpAsState(
                targetValue = if (!selected) spacing.extraLarge else spacing.medium,
                label = "corner"
            )
            val shape = RoundedCornerShape(corner)

            OutlinedCard(
                shape = shape,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = colorScheme.background,
                    contentColor = colorScheme.onBackground
                ),
                modifier = modifier
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                    }
                    .size(96.dp)
                    .interactionBorder(
                        type = InteractionType.PRESS,
                        source = interactionSource,
                        shape = shape,
                        color = colorScheme.primary
                    )
            ) {
                Box(
                    modifier = Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = {
                            if (selected) return@combinedClickable
                            feedback.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onClick()
                        },
                        onLongClick = {
                            feedback.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongClick()
                        }
                    ),
                    content = { content() }
                )
            }

            Crossfade(selected, label = "icon") { selected ->
                if (!selected) {
                    Icon(
                        imageVector = when (isDark) {
                            true -> Icons.Rounded.DarkMode
                            false -> Icons.Rounded.LightMode
                        },
                        contentDescription = "",
                        tint = when (isDark) {
                            true -> SugarColors.Tee
                            false -> SugarColors.Yellow
                        }
                    )
                }
            }
        } else {
            TvCard(
                colors = TvCardDefaults.colors(
                    containerColor = colorScheme.background,
                    contentColor = colorScheme.onBackground
                ),
                shape = TvCardDefaults.shape(
                    RoundedCornerShape(spacing.large)
                ),
                border = TvCardDefaults.border(focusedBorder = Border.None),
                scale = TvCardDefaults.scale(
                    scale = 0.8f,
                    focusedScale = 0.95f,
                    pressedScale = 0.85f
                ),
                onClick = {
                    if (selected) return@TvCard
                    onClick()
                },
                onLongClick = {
                    onLongClick()
                },
                modifier = modifier.size(96.dp),
                content = {
                    Box(contentAlignment = Alignment.Center) {
                        content()
                        Crossfade(selected, label = "icon") { selected ->
                            if (!selected) {
                                TvIcon(
                                    imageVector = when (isDark) {
                                        true -> Icons.Rounded.DarkMode
                                        false -> Icons.Rounded.LightMode
                                    },
                                    contentDescription = "",
                                    tint = when (isDark) {
                                        true -> SugarColors.Tee
                                        false -> SugarColors.Yellow
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ThemeAddSelection(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    Box(
        contentAlignment = Alignment.Center
    ) {
        OutlinedCard(
            shape = RoundedCornerShape(spacing.extraLarge),
            colors = CardDefaults.outlinedCardColors(containerColor, contentColor),
            elevation = CardDefaults.outlinedCardElevation(
                defaultElevation = spacing.none
            ),
            modifier = modifier
                .graphicsLayer {
                    scaleX = 0.8f
                    scaleY = 0.8f
                }
                .size(96.dp)
                .padding(spacing.extraSmall),
            onClick = onClick,
            content = {}
        )

        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "",
            tint = contentColor
        )
    }
}

@Composable
private fun ColorPiece(
    containerColor: Color,
    contentColor: Color,
    left: Boolean,
    contentDescription: String
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(
            topStart = spacing.small,
            topEnd = spacing.small,
            bottomStart = if (left) spacing.none else spacing.small,
            bottomEnd = if (!left) spacing.none else spacing.small
        ),
        modifier = Modifier
            .aspectRatio(1f)
            .requiredSize(if (left) 48.dp else 32.dp)
            .padding(spacing.extraSmall)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = contentDescription,
                style = MaterialTheme.typography.bodyLarge
                    .copy(
                        fontSize = if (left) 16.sp
                        else 12.sp
                    ),
                color = contentColor
            )
        }
    }
}
