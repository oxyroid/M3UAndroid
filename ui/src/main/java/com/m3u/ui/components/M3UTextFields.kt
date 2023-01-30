@file:Suppress("unused")

package com.m3u.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.ui.local.LocalDuration
import com.m3u.ui.local.LocalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun M3UTextField(
    textFieldValue: TextFieldValue,
    modifier: Modifier = Modifier,
    background: Color = M3UTextFieldDefaults.backgroundColor(),
    contentColor: Color = M3UTextFieldDefaults.contentColor(),
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction? = null,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions? = null,
    fontSize: TextUnit = M3UTextFieldDefaults.DefaultFontSize,
    height: Dp = M3UTextFieldDefaults.DefaultHeight,
    isError: Boolean = false,
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

    BasicTextField(
        value = textFieldValue,
        singleLine = singleLine,
        enabled = enabled,
        textStyle = TextStyle(
            fontFamily = MaterialTheme.typography.body1.fontFamily,
            fontSize = fontSize,
            color = contentColor,
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
            .focusable()
            .bringIntoViewRequester(bringIntoViewRequester)
            .fillMaxWidth(),
        readOnly = readOnly,
        cursorBrush = SolidColor(contentColor.copy(LocalContentAlpha.current)),
        decorationBox = { innerTextField ->
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (isError) LocalTheme.current.error else background)
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
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun M3UTextField(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = M3UTextFieldDefaults.backgroundColor(),
    contentColor: Color = M3UTextFieldDefaults.contentColor(),
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction? = null,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions? = null,
    fontSize: TextUnit = M3UTextFieldDefaults.DefaultFontSize,
    height: Dp = M3UTextFieldDefaults.DefaultHeight,
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

    BasicTextField(
        value = text,
        singleLine = singleLine,
        enabled = enabled,
        textStyle = TextStyle(
            fontFamily = MaterialTheme.typography.body1.fontFamily,
            fontSize = fontSize,
            color = contentColor,
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
            .focusable()
            .bringIntoViewRequester(bringIntoViewRequester)
            .fillMaxWidth(),
        readOnly = readOnly,
        cursorBrush = SolidColor(contentColor.copy(LocalContentAlpha.current)),
        decorationBox = { innerTextField ->
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (isError) LocalTheme.current.error else background)
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
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )
}

@ExperimentalLayoutApi
@ExperimentalFoundationApi
@Composable
fun M3UPasswordTextField(
    textFieldValue: TextFieldValue,
    modifier: Modifier = Modifier,
    background: Color = M3UTextFieldDefaults.backgroundColor(),
    contentColor: Color = M3UTextFieldDefaults.contentColor(),
    placeholder: String = "●●●●●●",
    height: Dp = M3UTextFieldDefaults.DefaultHeight,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions? = null,
    @DrawableRes icon: Int? = null,
    onValueChange: (TextFieldValue) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val duration = LocalDuration.current
    val passwordVisibility = remember { mutableStateOf(false) }
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

    val fontSize = if (passwordVisibility.value) M3UTextFieldDefaults.MinimizeFontSize
    else M3UTextFieldDefaults.PasswordFontSize

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
        visualTransformation =
        if (passwordVisibility.value) VisualTransformation.None
        else PasswordVisualTransformation(mask = '*'),
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
                    .clip(MaterialTheme.shapes.medium)
                    .background(background)
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

                    val animPlaceholder: Dp by animateDpAsState(if (isFocused.value || hasText) 6.dp else 14.dp)
                    val animPlaceHolderFontSize: Int by animateIntAsState(if (isFocused.value || hasText) 12 else 14)

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
                        overflow = TextOverflow.Ellipsis
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

                Spacer(Modifier.width(16.dp))

                IconButton(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    onClick = {
                        passwordVisibility.value = !passwordVisibility.value
                    }
                ) {
                    AnimatedVisibility(
                        visible = passwordVisibility.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Show Password",
                            tint = contentColor.copy(alpha = .35f)
                        )
                    }

                    AnimatedVisibility(
                        visible = !passwordVisibility.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = "Hide Password",
                            tint = contentColor.copy(alpha = .35f)
                        )
                    }
                }
            }
        },
        cursorBrush = SolidColor(contentColor.copy(.35f))
    )
}

private object M3UTextFieldDefaults {
    val DefaultFontSize = 16.sp
    val PasswordFontSize = 18.sp
    val MinimizeFontSize = 14.sp

    val DefaultHeight = 48.dp

    @Composable
    fun backgroundColor() = LocalTheme.current.surface

    @Composable
    fun contentColor() = LocalTheme.current.onSurface
}