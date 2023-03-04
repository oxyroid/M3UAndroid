@file:Suppress("unused")

package com.m3u.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
fun BottomSheetContent(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top
) {
    var offset by remember {
        mutableStateOf(0f)
    }
    val animateOffset by animateFloatAsState(offset)
    var pressed by remember {
        mutableStateOf(false)
    }
    val state = rememberDraggableState {
        offset += it
    }
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            color = theme.background,
            contentColor = theme.onBackground,
            shape = RoundedCornerShape(
                topStart = LocalSpacing.current.medium,
                topEnd = LocalSpacing.current.medium
            ),
            border = BorderStroke(2.dp, theme.divider.copy(alpha = 0.45f)),
            elevation = spacing.medium,
            modifier = Modifier
                .graphicsLayer {
                    translationY = animateOffset.coerceAtLeast(0f)
                }
        ) {
            val configuration = LocalConfiguration.current
            val feedback = LocalHapticFeedback.current
            val density = LocalDensity.current.density
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (maxHeight) it.fillMaxHeight()
                        else it.wrapContentHeight()
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = state,
                        onDragStarted = {
                            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            pressed = true
                        },
                        onDragStopped = {
                            if (offset > configuration.screenHeightDp * density / 4) {
                                onDismiss()
                            }
                            offset = 0f
                            pressed = false
                        },
                    )
                    .padding(LocalSpacing.current.medium),
            ) {
                val dividerColor by animateColorAsState(
                    if (pressed) theme.primary.copy(alpha = 0.45f) else theme.topBar
                )
                Divider(
                    color = dividerColor,
                    thickness = LocalSpacing.current.small,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(64.dp)
                        .clip(CircleShape)
                )
                Column(
                    verticalArrangement = verticalArrangement,
                    modifier = Modifier
                        .padding(
                            vertical = spacing.small
                        ),
                    content = content
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheetItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current
    Surface(
        shape = RoundedCornerShape(spacing.medium),
        elevation = 0.dp,
        color = theme.surface,
        contentColor = theme.onSurface,
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = modifier.padding(spacing.medium)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1
            )
        }
    }
}

typealias OnDismissRequest = () -> Unit
typealias OnConfirm = () -> Unit
typealias OnDismiss = () -> Unit

@Composable
fun AlertDialog(
    title: String,
    text: String,
    confirm: String?,
    dismiss: String?,
    onDismissRequest: OnDismissRequest,
    onConfirm: OnConfirm,
    onDismiss: OnDismiss,
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalTheme.current.background,
    contentColor: Color = LocalTheme.current.onBackground,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                maxLines = 1
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.body1
            )
        },
        confirmButton = {
            confirm?.let {
                TextButton(text = it, onClick = onConfirm)
            }
        },
        dismissButton = {
            dismiss?.let {
                TextButton(text = it, onClick = onDismiss)
            }
        },
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        modifier = modifier
    )
}