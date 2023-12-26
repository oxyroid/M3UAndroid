package com.m3u.androidApp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.androidApp.components.AppNavigation
import com.m3u.androidApp.components.AppSnackHost
import com.m3u.core.util.collections.withEach
import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.ActionHolder
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import com.m3u.ui.Navigate
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

@Composable
internal fun M3UScaffold(
    title: String,
    message: Message.Dynamic,
    actionHolder: ActionHolder,
    rootDestination: Destination.Root?,
    fob: Fob?,
    useRailNav: Boolean,
    navigate: Navigate,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val spacing = LocalSpacing.current

    M3UScaffoldImpl(
        title = title,
        useRailNav = useRailNav,
        actions = {
            val actions = actionHolder.actions
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
        modifier = modifier,
        navigation = {
            AppNavigation(
                navigate = navigate,
                rootDestination = rootDestination,
                fob = fob,
                useNavRail = useRailNav
            )
        }
    ) { padding ->
        Box {
            content(padding)
            AppSnackHost(
                message = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.small)
                    .align(Alignment.BottomCenter)
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun M3UScaffoldImpl(
    title: String,
    useRailNav: Boolean,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    onBackPressedContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    navigation: @Composable () -> Unit = {},
    content: @Composable BoxScope.(PaddingValues) -> Unit
) {
    val currentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background,
        label = "scaffold-color"
    )
    val hazeState = remember { HazeState() }
    // val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val actualContent = @Composable {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (onBackPressed != null) {
                            IconButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = onBackPressedContentDescription,
                                onClick = onBackPressed,
                                modifier = Modifier.wrapContentSize()
                            )
                        }
                    },
                    actions = actions,
                    // scrollBehavior = scrollBehavior,
                    modifier = Modifier
                        .hazeChild(hazeState)
                        .fillMaxWidth()
                )
            },
            modifier = modifier
                .fillMaxSize()
                // .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { padding ->
            Box(Modifier.haze(hazeState, currentColor)) {
                content(padding)
            }
        }
    }
    if (useRailNav) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigation()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                actualContent()
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                actualContent()
            }
            navigation()
        }
    }
}