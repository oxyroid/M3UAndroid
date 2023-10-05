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
import com.m3u.androidApp.components.AppBottomSheet
import com.m3u.androidApp.components.AppSnackHost
import com.m3u.core.util.withEach
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.TopLevelDestination
import com.m3u.ui.components.AppTopBar
import com.m3u.ui.components.IconButton
import com.m3u.ui.model.Action
import com.m3u.ui.model.Fob
import com.m3u.ui.model.Helper
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun AppScaffold(
    title: String,
    snacker: String,
    actions: List<Action>,
    topLevelDestinations: List<TopLevelDestination>,
    currentTopLevelDestination: TopLevelDestination?,
    fob: Fob?,
    isSystemBarVisible: Boolean,
    isSystemBarScrollable: Boolean,
    theme: Theme,
    helper: Helper,
    cinemaMode: Boolean,
    isPlaying: Boolean,
    navigateToTopLevelDestination: (TopLevelDestination) -> Unit,
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
            onBackPressed = onBackPressed
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
                            destinations = topLevelDestinations,
                            destination = currentTopLevelDestination,
                            navigateToTopLevelDestination = navigateToTopLevelDestination,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        foreground()
    }
}