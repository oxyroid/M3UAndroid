@file:Suppress("unused")

package com.m3u.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m3u.core.util.compose.ifUnspecified
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
fun DialogTextField(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onTextChange: (String) -> Unit = {},
    icon: ImageVector? = null,
    iconTint: Color = backgroundColor,
    readOnly: Boolean = true,
    onIconClick: (() -> Unit)? = null,
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = modifier
    ) {
        TextField(
            text = text,
            onValueChange = onTextChange,
            backgroundColor = if (readOnly) Color.Transparent
            else backgroundColor.ifUnspecified { theme.surface },
            contentColor = contentColor.ifUnspecified { theme.onSurface },
            readOnly = readOnly,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f)
        )

        if (onIconClick != null && icon != null) {
            IconButton(
                icon = icon,
                tint = iconTint.ifUnspecified { LocalTheme.current.onBackground },
                onClick = onIconClick,
                contentDescription = null
            )
        }
    }
}

@Composable
fun DialogItem(
    text: String,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current
    Surface(
        shape = RoundedCornerShape(spacing.medium),
        elevation = 0.dp,
        color = color.ifUnspecified { theme.surface },
        contentColor = contentColor.ifUnspecified { theme.onSurface }
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(spacing.medium)
                .fillMaxWidth()
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DialogTextField(
    resId: Int,
    color: Color = LocalTheme.current.onBackground,
    onTextChange: (String) -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = color,
    readOnly: Boolean = true,
    onIconClick: (() -> Unit)? = null,
) {
    DialogTextField(
        text = stringResource(id = resId),
        backgroundColor = color,
        onTextChange = onTextChange,
        icon = icon,
        iconTint = iconTint,
        readOnly = readOnly,
        onIconClick = onIconClick
    )
}

@Composable
fun DialogItem(
    resId: Int,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    DialogItem(
        text = stringResource(id = resId),
        color = color,
        contentColor = contentColor,
        onClick = onClick
    )
}

@Composable
fun AppDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(2.dp, LocalTheme.current.divider.copy(alpha = 0.45f)),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current

    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                color = theme.background,
                contentColor = theme.onBackground,
                shape = RoundedCornerShape(LocalSpacing.current.medium),
                border = border,
                elevation = spacing.medium,
                modifier = Modifier
                    .padding(spacing.medium)
                    .fillMaxWidth()
                    .wrapContentSize()
                    .animateContentSize()
                    .then(modifier)
            ) {
                Column(
                    verticalArrangement = verticalArrangement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    content = content
                )
            }
        }
    }
}

typealias OnDismissRequest = () -> Unit
typealias OnConfirm = () -> Unit
typealias OnDismiss = () -> Unit
