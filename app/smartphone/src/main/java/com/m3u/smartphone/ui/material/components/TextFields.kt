@file:Suppress("unused")

package com.m3u.smartphone.ui.material.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.InteractionType
import com.m3u.smartphone.ui.material.ktx.interactionBorder

@Composable
fun TextField(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = TextFieldDefaults.containerColor(),
    contentColor: Color = TextFieldDefaults.contentColor(),
    shape: Shape = TextFieldDefaults.shape(),
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction? = null,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions? = null,
    fontSize: TextUnit = TextFieldDefaults.TextFontSize,
    fontWeight: FontWeight? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    onValueChange: (String) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focus by interactionSource.collectIsFocusedAsState()
    val semanticErrorMessage = when {
        !isError -> null
        !errorMessage.isNullOrBlank() -> errorMessage
        else -> stringResource(string.ui_error_unknown)
    }

    BackHandler(focus) {
        focusManager.clearFocus()
    }

    val theme = MaterialTheme.colorScheme
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = theme.primary,
            backgroundColor = theme.primary.copy(alpha = 0.45f)
        )
    ) {
        BasicTextField(
            value = text,
            singleLine = singleLine,
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize = fontSize,
                color = contentColor,
                fontWeight = fontWeight
            ),
            onValueChange = {
                onValueChange(it)
            },
            keyboardActions = keyboardActions ?: KeyboardActions(
                onDone = { focusManager.clearFocus() },
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onSearch = { focusManager.clearFocus() }
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrectEnabled = false,
                imeAction = imeAction ?: if (singleLine) ImeAction.Done else ImeAction.Default
            ),
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            modifier = modifier
                .fillMaxWidth()
                .semantics {
                    if (placeholder.isNotBlank()) {
                        contentDescription = placeholder
                    }
                    if (semanticErrorMessage != null) {
                        error(semanticErrorMessage)
                    }
                }
                .focusRequester(focusRequester),
            readOnly = readOnly,
            cursorBrush = SolidColor(contentColor),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .clip(shape)
                        .background(
                            if (isError) MaterialTheme.colorScheme.error
                            else backgroundColor
                        )
                        .interactionBorder(
                            type = InteractionType.PRESS,
                            source = interactionSource,
                            shape = shape
                        )
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(
                            top = if (singleLine) 0.dp else 12.5.dp,
                            bottom = if (singleLine) 2.5.dp else 12.5.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    innerTextField()

                    if (text.isEmpty()) {
                        Text(
                            modifier = Modifier.clearTextFieldLabelSemantics(),
                            text = placeholder,
                            color = contentColor.copy(.35f),
                            fontSize = fontSize,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun PlaceholderField(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = TextFieldDefaults.containerColor(),
    contentColor: Color = TextFieldDefaults.contentColor(),
    placeholderColor: Color = contentColor.copy(alpha = 0.35f),
    shape: Shape = TextFieldDefaults.shape(),
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fontWeight: FontWeight? = null,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions? = null,
    icon: ImageVector? = null,
    textDirection: TextDirection = TextDirection.Unspecified,
    onValueChange: (String) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focus by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(focus, imeBottom) {
        if (focus && imeBottom > 0) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    BackHandler(focus) {
        focusManager.clearFocus()
    }

    val fontSize = TextFieldDefaults.MinimizeLabelFontSize

    val theme = MaterialTheme.colorScheme
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = theme.primary,
            backgroundColor = theme.primary.copy(alpha = 0.45f)
        )
    ) {
        BasicTextField(
            value = text,
            singleLine = singleLine,
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize = fontSize,
                color = contentColor,
                fontWeight = fontWeight,
                textDirection = textDirection,
            ),
            onValueChange = {
                onValueChange(it)
            },
            keyboardActions = keyboardActions ?: KeyboardActions(
                onDone = { focusManager.clearFocus() },
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onSearch = { focusManager.clearFocus() }
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrectEnabled = false,
                imeAction = imeAction
            ),
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            modifier = modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .fillMaxWidth()
                .semantics {
                    if (placeholder.isNotBlank()) {
                        contentDescription = placeholder
                    }
                }
                .focusRequester(focusRequester),
            readOnly = readOnly,
            cursorBrush = SolidColor(contentColor.copy(.35f)),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(backgroundColor)
                        .clickable(
                            enabled = enabled && !readOnly,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = focusRequester::requestFocus
                        )
                        .interactionBorder(
                            type = InteractionType.PRESS,
                            source = interactionSource,
                            shape = shape
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icon?.let { icon ->
                        Icon(
                            modifier = Modifier
                                .size(56.dp)
                                .padding(15.dp),
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor
                        )
                    }

                    Column(
                        Modifier
                            .interactionBorder(
                                type = InteractionType.PRESS,
                                source = interactionSource,
                                shape = shape
                            )
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .padding(
                                start = if (icon == null) 15.dp else 0.dp,
                                end = 15.dp,
                                top = 7.dp,
                                bottom = 7.dp
                            )
                    ) {
                        val hasText = text.isNotEmpty()
                        val showFloatingLabel = focus || hasText
                        val animPlaceHolderFontSize: Float by animateFloatAsState(
                            targetValue = if (showFloatingLabel) 12f else 14f,
                            label = "placeholder-font-size"
                        )

                        if (showFloatingLabel) {
                            Text(
                                modifier = Modifier.clearTextFieldLabelSemantics(),
                                text = placeholder,
                                color = placeholderColor,
                                fontSize = animPlaceHolderFontSize.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 18.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                innerTextField()
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 42.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    modifier = Modifier.clearTextFieldLabelSemantics(),
                                    text = placeholder,
                                    color = placeholderColor,
                                    fontSize = animPlaceHolderFontSize.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun Modifier.clearTextFieldLabelSemantics(): Modifier = clearAndSetSemantics {}

private object TextFieldDefaults {
    val TextFontSize = 16.sp
    val LabelFontSize = 18.sp
    val MinimizeLabelFontSize = 14.sp

    @Composable
    fun containerColor() = MaterialTheme.colorScheme.surfaceVariant

    @Composable
    fun contentColor() = MaterialTheme.colorScheme.onSurface

    @Composable
    fun shape() = AbsoluteRoundedCornerShape(16.dp)
}
