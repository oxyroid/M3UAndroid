package com.m3u.material.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.material.ktx.InteractionType
import com.m3u.material.ktx.interactionBorder
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.SugarColors

@Composable
fun ThemeSelection(
    colorScheme: ColorScheme,
    isDark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    leftContentDescription: String,
    rightContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val alpha by animateFloatAsState(
        if (selected) 0f else 0.4f,
        label = "alpha"
    )
    val zoom by animateFloatAsState(
        if (selected) 1f else 0.85f,
        label = "zoom"
    )
    val blurRadius by animateFloatAsState(
        if (selected) 0f else 16f,
        label = "blurRadius"
    )
    val feedback = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(spacing.medium)

    Box(
        contentAlignment = Alignment.Center
    ) {
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
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(),
                    onClick = {
                        if (selected) return@combinedClickable
                        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                    onLongClick = {
                        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .interactionBorder(
                    type = InteractionType.PRESS,
                    source = interactionSource,
                    shape = shape
                )
        ) {
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
                    text = if (isDark) "NIGHT" else "DAY",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.onTertiary)
                )
            }
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
    }
}

@Composable
fun ThemeAddSelection(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    Box(
        contentAlignment = Alignment.Center
    ) {
        OutlinedCard(
            shape = RoundedCornerShape(spacing.medium),
            colors = CardDefaults.outlinedCardColors(
                containerColor = theme.surface,
                contentColor = theme.onSurface
            ),
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
            tint = theme.onSurface
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
