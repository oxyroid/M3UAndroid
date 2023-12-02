package com.m3u.androidApp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.navigation.suite.NavigationSuiteScaffold
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.m3u.androidApp.components.AppSnackHost
import com.m3u.core.util.basic.title
import com.m3u.core.util.collections.withEach
import com.m3u.i18n.R.string
import com.m3u.material.components.IconButton
import com.m3u.material.components.ToolkitScaffold
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.ActionHolder
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import com.m3u.ui.Helper
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.Navigate
import com.m3u.ui.useRailNav
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun AppScaffold(
    title: String,
    snacker: String,
    useDynamicColors: Boolean,
    actionHolder: ActionHolder,
    rootDestination: Destination.Root?,
    fob: Fob?,
    isSystemBarScrollable: Boolean,
    helper: Helper,
    cinemaMode: Boolean,
    navigate: Navigate,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val spacing = LocalSpacing.current
    val useNavRail = helper.useRailNav

    M3ULocalProvider(
        helper = helper,
        useDynamicColors = useDynamicColors
    ) {
        val scope = rememberCoroutineScope()
        val darkMode = when {
            cinemaMode -> true
            else -> isSystemInDarkTheme()
        }
        DisposableEffect(
            darkMode,
            scope,
            cinemaMode
        ) {
            scope.launch {
                if (!cinemaMode) {
                    delay(800.milliseconds)
                }
                helper.darkMode = darkMode
            }
            onDispose {}
        }
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                val relation = fob?.rootDestination
                val actualActiveDestination = rootDestination ?: relation
                val roots = Destination.Root.entries
                roots.forEach { root ->
                    val fobbed = root == relation
                    val selected = root == actualActiveDestination
                    val iconTextId = root.iconTextId
                    val selectedIcon = fob?.icon.takeIf { fobbed } ?: root.selectedIcon
                    val unselectedIcon = fob?.icon.takeIf { fobbed } ?: root.unselectedIcon

                    item(
                        alwaysShowLabel = false,
                        selected = actualActiveDestination == root,
                        label = {
                            Text(
                                text = stringResource(iconTextId).title(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        icon = {
                            val contentDestination = stringResource(root.titleTextId)
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                state = rememberTooltipState(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(contentDestination)
                                    }
                                }
                            ) {
                                val icon = if (selected) selectedIcon
                                else unselectedIcon
                                Crossfade(
                                    targetState = icon,
                                    label = "app-scaffold-navigation-suite-scaffold"
                                ) { actualIcon ->
                                    Icon(
                                        imageVector = actualIcon,
                                        contentDescription = contentDestination
                                    )
                                }
                            }
                        },
                        onClick = {
                            if (fobbed) {
                                fob?.onClick?.invoke()
                            } else {
                                navigate(root)
                            }
                        }
                    )
                }
            }
        ) {
            ToolkitScaffold(
                title = title,
                scrollable = isSystemBarScrollable,
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
                modifier = modifier
            ) { padding ->
                Box {
                    val navRailModifier = if (useNavRail) Modifier.navigationBarsPadding()
                    else Modifier
                    content(padding)
                    AppSnackHost(
                        message = snacker,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.small)
                            .align(Alignment.BottomCenter)
                            .then(navRailModifier)
                    )
                }
            }
        }
    }
}