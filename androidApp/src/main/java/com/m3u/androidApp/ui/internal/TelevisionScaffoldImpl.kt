package com.m3u.androidApp.ui.internal

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.m3u.androidApp.ui.Items
import com.m3u.androidApp.ui.MainContent
import com.m3u.androidApp.ui.NavigationItemLayout
import com.m3u.androidApp.ui.ScaffoldLayout
import com.m3u.androidApp.ui.ScaffoldRole
import com.m3u.material.components.Background
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.helper.Metadata
import androidx.tv.material3.Border as TvBorder
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

@Composable
@InternalComposeApi
fun TvScaffoldImpl(
    rootDestination: Destination.Root?,
    navigateToRoot: (Destination.Root) -> Unit,
    onBackPressed: (() -> Unit)?,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val fob = Metadata.fob

    val navigation = @Composable {
        TvNavigation {
            Items { currentRootDestination ->
                NavigationItemLayout(
                    rootDestination = rootDestination,
                    fob = fob,
                    currentRootDestination = currentRootDestination,
                    navigateToRoot = navigateToRoot
                ) { selected: Boolean,
                    onClick: () -> Unit,
                    icon: @Composable () -> Unit,
                    _: @Composable () -> Unit ->
                    val source = remember { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    val currentContainerColor by with(TvMaterialTheme.colorScheme) {
                        animateColorAsState(
                            targetValue = when {
                                selected -> inverseSurface
                                focused -> primaryContainer.copy(0.67f)
                                else -> background
                            },
                            label = "scaffold-navigation-container"
                        )
                    }
                    val currentContentColor by with(TvMaterialTheme.colorScheme) {
                        animateColorAsState(
                            targetValue = when {
                                selected -> inverseOnSurface
                                focused -> onPrimaryContainer
                                else -> onBackground
                            },
                            label = "scaffold-navigation-content"
                        )
                    }
                    TvCard(
                        onClick = onClick,
                        colors = TvCardDefaults.colors(
                            containerColor = currentContainerColor,
                            contentColor = currentContentColor
                        ),
                        interactionSource = source,
                        shape = TvCardDefaults.shape(CircleShape),
                        border = TvCardDefaults.border(focusedBorder = TvBorder.None),
                        scale = TvCardDefaults.scale(
                            scale = if (selected) 1.1f else 1f,
                            focusedScale = if (selected) 1.2f else 1.1f
                        ),
                        content = {
                            Box(modifier = Modifier.padding(spacing.medium)) { icon() }
                        }
                    )
                }
            }
        }
    }
    val mainContent = @Composable { contentPadding: PaddingValues ->
        MainContent(
            windowInsets = WindowInsets.systemBars,
            onBackPressed = onBackPressed,
            content = { content(it + contentPadding) }
        )
    }

    Background(modifier) {
        ScaffoldLayout(
            role = ScaffoldRole.Tv,
            navigation = navigation,
            mainContent = mainContent
        )
    }
}

@Composable
private fun TvNavigation(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalSpacing.current
    Column(
        verticalArrangement = Arrangement.spacedBy(
            spacing.medium,
            Alignment.CenterVertically
        ),
        modifier = modifier
            .fillMaxHeight()
            .padding(spacing.medium)
    ) {
        content()
    }
}