@file:Suppress("unused")

package com.m3u.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.m3u.material.ktx.tv
import com.m3u.material.model.LocalSpacing
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

@Composable
fun Button(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor: Color = containerColor.copy(alpha = 0.12f),
    disabledContentColor: Color = containerColor.copy(alpha = 0.38f),
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    val tv = tv()
    if (!tv) {
        Button(
            shape = RoundedCornerShape(8.dp),
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor
            )
        ) {
            Text(
                text = text.uppercase()
            )
        }
    } else {
        TvButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .padding(spacing.extraSmall)
                .then(modifier),
            colors = androidx.tv.material3.ButtonDefaults.colors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor
            )
        ) {
            TvText(
                text = text.uppercase()
            )
        }
    }

}

@Composable
fun TextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current

    val tv = tv()
    if (!tv) {
        TextButton(
            shape = RoundedCornerShape(8.dp),
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContentColor = disabledContentColor
            )
        ) {
            Text(
                text = text.uppercase()
            )
        }
    } else {
        TvOutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .padding(spacing.extraSmall)
                .then(modifier),
            colors = androidx.tv.material3.ButtonDefaults.colors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContentColor = disabledContentColor
            )
        ) {
            TvText(
                text = text.uppercase()
            )
        }
    }
}

@Composable
fun TonalButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current

    val tv = tv()
    if (!tv) {
        FilledTonalButton(
            shape = RoundedCornerShape(8.dp),
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            elevation = ButtonDefaults.filledTonalButtonElevation(spacing.none)
        ) {
            Text(
                text = text.uppercase()
            )
        }
    } else {
        TvOutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .padding(spacing.extraSmall)
                .then(modifier),
        ) {
            TvText(
                text = text.uppercase()
            )
        }
    }
}

@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified
) {
    val tv = tv()
    if (!tv) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = if (tint.isUnspecified) IconButtonDefaults.iconButtonColors()
            else IconButtonDefaults.iconButtonColors(
                contentColor = tint
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    } else {
        androidx.tv.material3.IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = if (tint.isUnspecified) androidx.tv.material3.IconButtonDefaults.colors()
            else androidx.tv.material3.IconButtonDefaults.colors(
                contentColor = tint,
                focusedContainerColor = tint,
                pressedContainerColor = tint
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
fun BrushButton(
    text: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            ProvideTextStyle(value = MaterialTheme.typography.titleSmall) {
                Row(
                    Modifier
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = ButtonDefaults.MinHeight
                        )
                        .padding(ButtonDefaults.ContentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        Text(
                            text = text,
                            maxLines = 1
                        )
                    }
                )
            }
        }
    }
}
