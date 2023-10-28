@file:Suppress("unused")
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.m3u.material.components

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.material.ktx.InteractionType
import com.m3u.material.ktx.animateDp
import com.m3u.material.ktx.animateInt
import com.m3u.material.ktx.interactionBorder
import com.m3u.material.model.LocalDuration
import com.m3u.material.model.LocalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TextField(
    textFieldValue: TextFieldValue,
    modifier: Modifier = Modifier,
    background: Color = TextFieldDefaults.backgroundColor(),
    contentColor: Color = TextFieldDefaults.contentColor(),
    shape: Shape = TextFieldDefaults.shape(),
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction? = null,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions? = null,
    fontSize: TextUnit = TextFieldDefaults.TextFontSize,
    fontWeight: FontWeight? = null,
    height: Dp = TextFieldDefaults.Height,
    isError: Boolean = false,
    onValueChange: (TextFieldValue) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val duration = LocalDuration.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val interactionSourceFocus by interactionSource.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()

    val imeVisible = WindowInsets.isImeVisible

    // Bring the composable into view (visible to user).
    LaunchedEffect(imeVisible, interactionSourceFocus) {
        if (imeVisible && interactionSourceFocus) {
            scope.launch {
                delay(duration.fast.toLong())
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    val theme = LocalTheme.current
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = theme.primary,
            backgroundColor = theme.primary.copy(alpha = 0.45f)
        )
    ) {
        BasicTextField(
            value = textFieldValue,
            singleLine = singleLine,
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = MaterialTheme.typography.body1.fontFamily,
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
                imeAction = imeAction ?: if (singleLine) ImeAction.Done else ImeAction.Default
            ),
            interactionSource = interactionSource,
            modifier = modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .fillMaxWidth(),
            readOnly = readOnly,
            cursorBrush = SolidColor(contentColor.copy(LocalContentAlpha.current)),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .clip(shape)
                        .background(if (isError) LocalTheme.current.error else background)
                        .interactionBorder(
                            type = InteractionType.PRESS,
                            source = interactionSource,
                            shape = shape
                        )
                        .height(height)
                        .padding(horizontal = 12.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (singleLine) 0.dp else 12.5.dp,
                                bottom = if (singleLine) 2.5.dp else 12.5.dp
                            )
                    ) {
                        innerTextField()

                        if (textFieldValue.text.isEmpty()) {
                            Text(
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
            }
        )
    }
}

@Composable
fun TextField(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = TextFieldDefaults.backgroundColor(),
    contentColor: Color = TextFieldDefaults.contentColor(),
    shape: Shape = TextFieldDefaults.shape(),
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction? = null,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions? = null,
    fontSize: TextUnit = TextFieldDefaults.TextFontSize,
    height: Dp = TextFieldDefaults.Height,
    fontWeight: FontWeight? = null,
    isError: Boolean = false,
    onValueChange: (String) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val duration = LocalDuration.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val interactionSourceState by interactionSource.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()

    val imeVisible = WindowInsets.isImeVisible

    // Bring the composable into view (visible to user).
    LaunchedEffect(imeVisible, interactionSourceState) {
        if (imeVisible && interactionSourceState) {
            scope.launch {
                delay(duration.fast.toLong())
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    val theme = LocalTheme.current
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
                fontFamily = MaterialTheme.typography.body1.fontFamily,
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
                imeAction = imeAction ?: if (singleLine) ImeAction.Done else ImeAction.Default
            ),
            interactionSource = interactionSource,
            modifier = modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .fillMaxWidth(),
            readOnly = readOnly,
            cursorBrush = SolidColor(contentColor.copy(LocalContentAlpha.current)),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .clip(shape)
                        .background(if (isError) theme.error else backgroundColor)
                        .interactionBorder(
                            type = InteractionType.PRESS,
                            source = interactionSource,
                            shape = shape
                        )
                        .height(height)
                        .padding(horizontal = 12.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (singleLine) 0.dp else 12.5.dp,
                                bottom = if (singleLine) 2.5.dp else 12.5.dp
                            )
                    ) {
                        innerTextField()

                        if (text.isEmpty()) {
                            Text(
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
            }
        )
    }
}

@Composable
fun LabelField(
    textFieldValue: TextFieldValue,
    modifier: Modifier = Modifier,
    background: Color = TextFieldDefaults.backgroundColor(),
    contentColor: Color = TextFieldDefaults.contentColor(),
    shape: Shape = TextFieldDefaults.shape(),
    placeholder: String = "",
    height: Dp = TextFieldDefaults.Height,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions? = null,
    @DrawableRes icon: Int? = null,
    onValueChange: (TextFieldValue) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val duration = LocalDuration.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val interactionSourceState by interactionSource.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    val imeVisible = WindowInsets.isImeVisible

    // Bring the composable into view (visible to user).
    LaunchedEffect(imeVisible, interactionSourceState) {
        if (imeVisible && interactionSourceState) {
            scope.launch {
                delay(duration.fast.toLong())
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    val focusRequester = FocusRequester()
    val isFocused = remember { mutableStateOf(false) }

    val fontSize = TextFieldDefaults.MinimizeLabelFontSize

    val theme = LocalTheme.current
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = theme.primary,
            backgroundColor = theme.primary.copy(alpha = 0.45f)
        )
    ) {
        BasicTextField(
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused.value = it.isFocused
                }
                .bringIntoViewRequester(bringIntoViewRequester)
                .fillMaxWidth(),
            interactionSource = interactionSource,
            enabled = enabled,
            value = textFieldValue,
            singleLine = true,
            onValueChange = onValueChange,
            keyboardActions = keyboardActions ?: KeyboardActions(
                onDone = { focusManager.clearFocus() },
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction
            ),
            readOnly = readOnly,
            textStyle = TextStyle(
                fontSize = fontSize,
                fontFamily = MaterialTheme.typography.body1.fontFamily,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            ),
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .clip(shape)
                        .background(background)
                        .interactionBorder(
                            type = InteractionType.PRESS,
                            source = interactionSource,
                            shape = shape
                        )
                        .height(height),
                ) {
                    icon?.let {
                        Image(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(15.dp),
                            painter = painterResource(id = icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(contentColor)
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                start = if (icon == null) 15.dp else 0.dp,
                                bottom = 0.dp,
                                end = 15.dp
                            )
                    ) {
                        val hasText = textFieldValue.text.isNotEmpty()

                        val animPlaceholder: Dp by animateDp("PlaceholderTranslationY") {
                            if (isFocused.value || hasText) 6.dp else 14.dp
                        }
                        val animPlaceHolderFontSize: Int by animateInt("PlaceholderFontSize") {
                            if (isFocused.value || hasText) 12 else 14
                        }

                        Text(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = animPlaceholder.toPx()
                                },
                            text = placeholder,
                            color = contentColor.copy(alpha = .35f),
                            fontSize = animPlaceHolderFontSize.sp,
                            fontFamily = MaterialTheme.typography.body1.fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )

                        Box(
                            Modifier
                                .padding(top = 21.dp)
                                .fillMaxWidth()
                                .height(18.dp),
                        ) {
                            innerTextField()
                        }
                    }
                }
            },
            cursorBrush = SolidColor(contentColor.copy(.35f))
        )
    }
}

@Composable
fun LabelField(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = TextFieldDefaults.backgroundColor(),
    contentColor: Color = TextFieldDefaults.contentColor(),
    shape: Shape = TextFieldDefaults.shape(),
    placeholder: String = "",
    height: Dp = TextFieldDefaults.Height,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions? = null,
    icon: ImageVector? = null,
    onValueChange: (String) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val duration = LocalDuration.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val interactionSourceFocus by interactionSource.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    val imeVisible = WindowInsets.isImeVisible

    // Bring the composable into view (visible to user).
    LaunchedEffect(imeVisible, interactionSourceFocus) {
        Log.e("TAG", "ime: $imeVisible")
        if (imeVisible && interactionSourceFocus) {
            scope.launch {
                delay(duration.fast.toLong())
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    val focusRequester = FocusRequester()
    val isFocused = remember { mutableStateOf(false) }

    val fontSize = TextFieldDefaults.MinimizeLabelFontSize

    val theme = LocalTheme.current
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = theme.primary,
            backgroundColor = theme.primary.copy(alpha = 0.45f)
        )
    ) {
        BasicTextField(
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused.value = it.isFocused
                }
                .bringIntoViewRequester(bringIntoViewRequester)
                .fillMaxWidth(),
            interactionSource = interactionSource,
            enabled = enabled,
            value = text,
            singleLine = true,
            onValueChange = onValueChange,
            keyboardActions = keyboardActions ?: KeyboardActions(
                onDone = { focusManager.clearFocus() },
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction
            ),
            readOnly = readOnly,
            textStyle = TextStyle(
                fontSize = fontSize,
                fontFamily = MaterialTheme.typography.body1.fontFamily,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            ),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(background)
                        .interactionBorder(
                            type = InteractionType.PRESS,
                            source = interactionSource,
                            shape = shape
                        )
                        .height(height)
                ) {
                    icon?.let {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(15.dp),
                            tint = contentColor
                        )
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                start = if (icon == null) 15.dp else 0.dp,
                                bottom = 0.dp,
                                end = 15.dp
                            )
                    ) {
                        val hasText = text.isNotEmpty()

                        val animPlaceholder: Dp by animateDp("PlaceholderTranslationY") {
                            if (isFocused.value || hasText) 6.dp else 14.dp
                        }
                        val animPlaceHolderFontSize: Int by animateInt("PlaceholderFontSize") {
                            if (isFocused.value || hasText) 12 else 14
                        }

                        Text(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = animPlaceholder.toPx()
                                },
                            text = placeholder,
                            color = contentColor.copy(alpha = .35f),
                            fontSize = animPlaceHolderFontSize.sp,
                            fontFamily = MaterialTheme.typography.body1.fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )

                        Box(
                            Modifier
                                .padding(top = 21.dp)
                                .fillMaxWidth()
                                .height(18.dp),
                        ) {
                            innerTextField()
                        }
                    }
                }
            },
            cursorBrush = SolidColor(contentColor.copy(.35f))
        )
    }
}

private object TextFieldDefaults {
    val TextFontSize = 16.sp
    val LabelFontSize = 18.sp
    val MinimizeLabelFontSize = 14.sp

    val Height = 48.dp

    @Composable
    fun backgroundColor() = LocalTheme.current.surface

    @Composable
    fun contentColor() = LocalTheme.current.onSurface

    @Composable
    fun shape() = RoundedCornerShape(25)
}
