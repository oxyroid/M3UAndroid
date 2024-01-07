package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigation.suite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigation.suite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.androidApp.components.AppSnackHost
import com.m3u.core.wrapper.Message
import com.m3u.material.components.Background
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Action
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun M3UScaffold(
    title: String,
    message: Message.Dynamic,
    actions: ImmutableList<Action>,
    rootDestination: Destination.Root?,
    fob: Fob?,
    navigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val spacing = LocalSpacing.current

    M3UScaffoldImpl(
        title = title,
        actions = {
            actions.forEach {
                IconButton(
                    icon = it.icon,
                    contentDescription = it.contentDescription,
                    onClick = it.onClick
                )
            }
        },
        onBackPressed = onBackPressed,
        navigation = {
            val roots = Destination.Root.entries
            roots.forEach { root ->
                val useFob = fob?.rootDestination == root
                val selected = root == rootDestination || useFob
                item(
                    selected = selected,
                    onClick = {
                        if (useFob) fob?.onClick?.invoke()
                        else navigate(root)
                    },
                    icon = {
                        Icon(
                            imageVector = if (useFob && fob != null) fob.icon
                            else if (selected) root.selectedIcon
                            else root.unselectedIcon,
                            contentDescription = stringResource(
                                if (useFob && fob != null) fob.iconTextId
                                else root.iconTextId
                            )
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(
                                if (useFob && fob != null) fob.iconTextId
                                else root.iconTextId
                            )
                        )
                    },
                    alwaysShowLabel = false
                )
            }
        },
        content = { padding ->
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
        },
        modifier = modifier
    )
}

@Composable
private fun M3UScaffoldImpl(
    title: String,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    onBackPressedContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    navigation: NavigationSuiteScope.() -> Unit = {},
    content: @Composable BoxScope.(PaddingValues) -> Unit
) {
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
                    modifier = Modifier.fillMaxWidth()
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Background {
                content(padding)
            }
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = navigation,
        content = actualContent,
        modifier = modifier
    )
}