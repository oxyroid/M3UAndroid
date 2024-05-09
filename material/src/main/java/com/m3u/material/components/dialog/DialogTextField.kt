package com.m3u.material.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.m3u.material.components.Icon
import com.m3u.material.components.IconButton
import com.m3u.material.components.TextField
import com.m3u.material.model.LocalSpacing

@Composable
fun DialogTextField(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    placeholder: String = "",
    onTextChange: (String) -> Unit = {},
    leadingIcon: ImageVector? = null,
    trainingIcon: ImageVector? = null,
    iconTint: Color = backgroundColor,
    readOnly: Boolean = true,
    onTrainingIconClick: (() -> Unit)? = null,
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = modifier
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                tint = iconTint.takeOrElse { MaterialTheme.colorScheme.onBackground },
                contentDescription = null
            )
        }
        TextField(
            text = text,
            onValueChange = onTextChange,
            backgroundColor = if (readOnly) Color.Transparent
            else backgroundColor.takeOrElse { theme.surface },
            contentColor = contentColor.takeOrElse { theme.onSurface },
            readOnly = readOnly,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f),
            placeholder = placeholder
        )

        if (onTrainingIconClick != null && trainingIcon != null) {
            IconButton(
                icon = trainingIcon,
                tint = iconTint.takeOrElse { MaterialTheme.colorScheme.onBackground },
                onClick = onTrainingIconClick,
                contentDescription = null
            )
        }
    }
}

@Composable
fun DialogTextField(
    resId: Int,
    onTextChange: (String) -> Unit,
    color: Color = MaterialTheme.colorScheme.onBackground,
    icon: ImageVector? = null,
    iconTint: Color = color,
    readOnly: Boolean = true,
    onIconClick: (() -> Unit)? = null
) {
    DialogTextField(
        text = stringResource(id = resId),
        backgroundColor = color,
        onTextChange = onTextChange,
        trainingIcon = icon,
        iconTint = iconTint,
        readOnly = readOnly,
        onTrainingIconClick = onIconClick
    )
}
