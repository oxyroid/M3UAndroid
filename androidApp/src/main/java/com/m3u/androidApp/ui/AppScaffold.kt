package com.m3u.androidApp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.androidApp.components.AppBottomSheet
import com.m3u.androidApp.components.AppSnackHost
import com.m3u.core.util.withEach
import com.m3u.i18n.R.string
import com.m3u.material.components.AppTopBar
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.Theme
import com.m3u.ui.Action
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import com.m3u.ui.Helper
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.Navigate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun AppScaffold(
    title: String,
    snacker: String,
    actions: List<Action>,
    rootDestination: Destination.Root?,
    fob: Fob?,
    isSystemBarVisible: Boolean,
    isSystemBarScrollable: Boolean,
    theme: Theme,
    helper: Helper,
    cinemaMode: Boolean,
    isPlaying: Boolean,
    navigate: Navigate,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    foreground: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val spacing = LocalSpacing.current

    M3ULocalProvider(theme, helper) {
        val scope = rememberCoroutineScope()
        val darkMode = when {
            cinemaMode -> true
            isPlaying -> true
            else -> isSystemInDarkTheme()
        }
        DisposableEffect(
            darkMode,
            scope,
            isPlaying,
            cinemaMode
        ) {
            scope.launch {
                if (!cinemaMode && isPlaying) {
                    delay(800.milliseconds)
                }
                helper.darkMode = darkMode
            }
            onDispose {}
        }
        AppTopBar(
            title = title,
            visible = isSystemBarVisible,
            scrollable = isSystemBarScrollable,
            actions = {
                actions.withEach {
                    IconButton(
                        icon = icon,
                        contentDescription = contentDescription,
                        onClick = onClick
                    )
                }
            },
            onBackPressed = onBackPressed,
            onBackPressedContentDescription = stringResource(string.ui_cd_top_bar_on_back_pressed),
            modifier = modifier
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    content()
                    AppSnackHost(
                        message = snacker,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.small)
                            .align(Alignment.BottomCenter)
                    )
                }
                AnimatedContent(
                    targetState = isSystemBarVisible,
                    transitionSpec = {
                        slideInVertically { it } togetherWith slideOutVertically { it }
                    },
                    label = "AppBottomSheet",
                    modifier = Modifier.fillMaxWidth(),
                ) { visible ->
                    if (visible) {
                        AppBottomSheet(
                            fob = fob,
                            rootDestination = rootDestination,
                            navigate = navigate,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        foreground()
    }
}